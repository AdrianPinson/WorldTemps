package io.github.adrianpinson.worldtemps.ui;

import io.github.adrianpinson.worldtemps.model.Reading;
import io.github.adrianpinson.worldtemps.service.DashboardQuery.Location;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.chart.LineChart;
import javafx.scene.control.*;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Real JavaFX controls and layout; run with mvn -Pui-tests verify under a display or Xvfb. */
class DashboardUiIT {
    private static final java.util.List<Throwable> UI_ERRORS = new java.util.concurrent.CopyOnWriteArrayList<>();
    private DashboardView view;
    private Stage stage;
    @TempDir Path temporary;

    @BeforeAll static void startToolkit() throws Exception {
        CountDownLatch ready = new CountDownLatch(1);
        Platform.startup(() -> {
            Thread.currentThread().setUncaughtExceptionHandler((thread, error) -> UI_ERRORS.add(error));
            Platform.setImplicitExit(false);
            ready.countDown();
        });
        assertTrue(ready.await(15, TimeUnit.SECONDS), "JavaFX did not start");
    }

    @BeforeEach void openDashboard() throws Exception {
        CompletableFuture<Void> loaded = fx(() -> {
            view = new DashboardView();
            stage = new Stage();
            Scene scene = new Scene(view, 1440, 1000);
            scene.getStylesheets().add(getClass().getResource("/ui/dashboard.css").toExternalForm());
            stage.setScene(scene);
            stage.show();
            return view.loadDemo();
        });
        loaded.get(15, TimeUnit.SECONDS);
        fx(() -> { view.applyCss(); view.layout(); return null; });
    }

    @AfterEach void closeDashboard() throws Exception {
        fx(() -> { view.close(); stage.close(); return null; });
        assertTrue(UI_ERRORS.isEmpty(), () -> "Uncaught JavaFX error: " + UI_ERRORS);
    }

    @AfterAll static void stopToolkit() {
        Platform.exit();
    }

    @Test void linkedFiltersUpdateCardsChartAndTable() throws Exception {
        fx(() -> {
            assertEquals("90", label("count-value").getText());
            assertFalse(button("apply-filters").isDisabled());
            combo("country-filter").setValue("UK");
            ComboBox<Location> cities = combo("city-filter");
            assertEquals(2, cities.getItems().size());
            cities.setValue(cities.getItems().get(1));
            date("from-date").setValue(LocalDate.of(2025, 1, 1));
            date("to-date").setValue(LocalDate.of(2025, 1, 1));
            button("apply-filters").fire();
            assertEquals("1", label("count-value").getText());
            assertEquals("37.0°", label("average-value").getText());
            assertEquals(1, table().getItems().size());
            assertEquals("London", table().getItems().get(0).city());
            assertEquals(1, chart().getData().size());
            assertEquals(1, chart().getData().get(0).getData().size());
            return null;
        });
    }

    @Test void resetShowsAllLocationsWithoutJoiningUnrelatedCitiesOnTheChart() throws Exception {
        fx(() -> {
            button("reset-filters").fire();
            assertEquals("270", label("count-value").getText());
            assertEquals(270, table().getItems().size());
            assertTrue(chart().getData().isEmpty());
            assertTrue(label("chart-empty").isVisible());
            assertEquals("All regions", combo("region-filter").getValue());
            return null;
        });
    }

    @Test void invalidDatesPreserveTheCurrentResultsAndResetRecovers() throws Exception {
        fx(() -> {
            date("from-date").setValue(LocalDate.of(2026, 1, 1));
            button("apply-filters").fire();
            assertTrue(label("filter-feedback").isVisible());
            assertEquals("90", label("count-value").getText());
            button("reset-filters").fire();
            date("from-date").getEditor().setText("not-a-date");
            button("apply-filters").fire();
            assertTrue(label("filter-feedback").isVisible());
            button("reset-filters").fire();
            assertFalse(label("filter-feedback").isVisible());
            assertEquals("270", label("count-value").getText());
            return null;
        });
    }

    @Test void emptyMatchesClearThePlotAndDoNotInventTemperatures() throws Exception {
        fx(() -> {
            date("from-date").setValue(LocalDate.of(2030, 1, 1));
            date("to-date").setValue(LocalDate.of(2030, 2, 1));
            button("apply-filters").fire();
            assertEquals("0", label("count-value").getText());
            assertEquals("—", label("average-value").getText());
            assertTrue(table().getItems().isEmpty());
            assertTrue(chart().getData().isEmpty());
            assertTrue(label("chart-empty").isVisible());
            return null;
        });
    }

    @Test void numericTemperatureSortingWorks() throws Exception {
        fx(() -> {
            var column = table().getColumns().get(5);
            column.setSortType(TableColumn.SortType.DESCENDING);
            table().getSortOrder().setAll(java.util.List.of(column));
            table().sort();
            assertEquals(89, table().getItems().get(0).averageTemperatureF());
            for (int i = 1; i < table().getItems().size(); i++) {
                assertTrue(table().getItems().get(i - 1).averageTemperatureF() >= table().getItems().get(i).averageTemperatureF());
            }
            return null;
        });
    }

    @Test void importsReportRejectionsAndFailuresKeepTheExistingDataset() throws Exception {
        Path file = temporary.resolve("data.csv");
        Files.writeString(file, "bad,header\n");
        CompletableFuture<Void> failed = fx(() -> view.importFile(file));
        assertThrows(java.util.concurrent.ExecutionException.class, () -> failed.get(15, TimeUnit.SECONDS));
        fx(() -> {
            assertEquals("90", label("count-value").getText());
            assertTrue(label("status-label").getText().contains("previous dataset retained"));
            assertFalse(button("import-csv").isDisabled());
            return null;
        });
        Files.writeString(file, "Region,Country,State,City,Month,Day,Year,AvgTemperature\n"
                + "Europe,UK,,London,1,1,2025,-99\nEurope,UK,,London,1,2,2025,40\n");
        fx(() -> view.importFile(file)).get(15, TimeUnit.SECONDS);
        fx(() -> {
            assertEquals("1", label("count-value").getText());
            assertEquals("40.0°", label("average-value").getText());
            assertTrue(label("status-label").getText().contains("1 rejected"));
            assertFalse(button("apply-filters").isDisabled());
            return null;
        });
    }

    @Test void layoutFitsAndCapturesTheActualDesktop() throws Exception {
        fx(() -> {
            view.applyCss();
            view.layout();
            WritableImage snapshot = view.snapshot(null, null);
            BufferedImage image = new BufferedImage((int) snapshot.getWidth(), (int) snapshot.getHeight(), BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    image.setRGB(x, y, snapshot.getPixelReader().getArgb(x, y));
                }
            }
            Path output = Path.of("target/ui/dashboard.png");
            Files.createDirectories(output.getParent());
            ImageIO.write(image, "png", output.toFile());
            assertTrue(chart().getWidth() > 600);
            stage.setWidth(1080);
            stage.setHeight(780);
            return null;
        });
        fx(() -> {
            view.applyCss();
            view.layout();
            assertTrue(button("apply-filters").getWidth() > 150);
            assertTrue(table().getWidth() > 600);
            assertFalse(label("count-value").getText().isBlank());
            return null;
        });
    }

    private Label label(String id) { return (Label) view.lookup("#" + id); }
    private Button button(String id) { return (Button) view.lookup("#" + id); }
    private DatePicker date(String id) { return (DatePicker) view.lookup("#" + id); }
    @SuppressWarnings("unchecked") private <T> ComboBox<T> combo(String id) { return (ComboBox<T>) view.lookup("#" + id); }
    @SuppressWarnings("unchecked") private TableView<Reading> table() { return (TableView<Reading>) view.lookup("#readings-table"); }
    @SuppressWarnings("unchecked") private LineChart<Number, Number> chart() { return (LineChart<Number, Number>) view.lookup("#temperature-chart"); }

    private static <T> T fx(java.util.concurrent.Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(action);
        Platform.runLater(task);
        return task.get(20, TimeUnit.SECONDS);
    }
}
