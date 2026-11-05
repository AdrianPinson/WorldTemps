package io.github.adrianpinson.worldtemps.ui;

import io.github.adrianpinson.worldtemps.model.Reading;
import io.github.adrianpinson.worldtemps.service.DashboardQuery.Location;
import io.github.adrianpinson.worldtemps.service.TemperatureAnomalies;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;
import java.util.function.Supplier;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.concurrent.Task;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Window;

/** An explicit loaded-data analysis: no hidden API requests and no zero-filled observations. */
final class AnomalyDialog {
    static Dialog<Void> show(Window owner, Location location, int firstYear, int lastYear,
                     Supplier<List<Reading>> readings, ExecutorService worker) {
        Dialog<Void> dialog = new Dialog<>(); dialog.initOwner(owner);
        dialog.setTitle("Monthly temperature anomalies");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        TextField start = new TextField(Integer.toString(Math.min(firstYear, lastYear - 1)));
        TextField end = new TextField(Integer.toString(lastYear - 1));
        TextField target = new TextField(Integer.toString(lastYear));
        start.setId("baseline-start"); end.setId("baseline-end"); target.setId("anomaly-year");
        Button calculate = new Button("Calculate anomalies"); calculate.setId("calculate-anomalies");
        Label message = new Label("Only the primary city's loaded data is used, across all loaded dates. Date filters do not restrict the baseline.");
        message.setWrapText(true); message.setMaxWidth(750);
        Label definition = new Label("Anomaly = target monthly mean − baseline monthly mean (°F). Baseline years have equal weight. Missing months show —; partial coverage is not a complete climate normal.");
        definition.setWrapText(true); definition.setMaxWidth(750);
        TableView<TemperatureAnomalies.Month> table = new TableView<>(); table.setId("anomaly-table");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN); table.setPrefHeight(400);
        add(table, "Month", m -> Month.of(m.month()).getDisplayName(TextStyle.SHORT, Locale.US));
        add(table, "Baseline °F", m -> number(m.baselineF()));
        add(table, "Target °F", m -> number(m.targetF()));
        add(table, "Anomaly °F", m -> number(m.anomalyF()));
        add(table, "Baseline coverage", m -> String.format(Locale.US, "%d/%d days (%.1f%%); %d years", m.baselineDays(), m.baselineExpectedDays(), m.baselineCoverage(), m.baselineYearsObserved()));
        add(table, "Target coverage", m -> String.format(Locale.US, "%d/%d days (%.1f%%)", m.targetDays(), m.targetExpectedDays(), m.targetCoverage()));
        VBox content = new VBox(12, new Label(location.toString()), field("Baseline first year", start),
                field("Baseline last year", end), field("Target year", target), calculate, message, table, definition);
        content.setPrefWidth(780); dialog.getDialogPane().setContent(content);
        final Task<?>[] active = new Task<?>[1];
        calculate.setOnAction(event -> {
            try {
                int a = Integer.parseInt(start.getText().trim()), b = Integer.parseInt(end.getText().trim()), y = Integer.parseInt(target.getText().trim());
                calculate.setDisable(true); table.getItems().clear(); message.setText("Analyzing loaded observations…");
                Task<TemperatureAnomalies.Report> task = new Task<>() {
                    @Override protected TemperatureAnomalies.Report call() { return TemperatureAnomalies.analyze(readings.get(), location, a, b, y); }
                };
                active[0] = task;
                task.setOnSucceeded(done -> {
                    if (!dialog.isShowing()) return;
                    calculate.setDisable(false); table.getItems().setAll(task.getValue().months());
                    message.setText("Baseline " + a + "–" + b + " · Target " + y + " · All loaded dates · Daily duplicates averaged once per date.");
                });
                task.setOnFailed(done -> { if (dialog.isShowing()) { calculate.setDisable(false); message.setText(task.getException().getMessage()); } });
                worker.submit(task);
            } catch (NumberFormatException invalid) { message.setText("Enter whole-number years."); }
        });
        dialog.setOnHidden(event -> { if (active[0] != null) active[0].cancel(true); });
        dialog.show();
        return dialog;
    }
    private static HBox field(String text, TextField input) {
        Label label = new Label(text); label.setLabelFor(input); label.setMinWidth(150); return new HBox(12, label, input);
    }
    private static String number(Double value) { return value == null ? "—" : String.format(Locale.US, "%.2f", value); }
    private static void add(TableView<TemperatureAnomalies.Month> table, String title, Function<TemperatureAnomalies.Month, String> value) {
        TableColumn<TemperatureAnomalies.Month, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(value.apply(cell.getValue())));
        column.setPrefWidth(title.contains("coverage") ? 240 : 100); table.getColumns().add(column);
    }
}
