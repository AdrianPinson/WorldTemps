package io.github.adrianpinson.worldtemps.ui;

import io.github.adrianpinson.worldtemps.importer.CsvTemperatureLoader;
import io.github.adrianpinson.worldtemps.importer.CsvTemperatureLoader.ImportResult;
import io.github.adrianpinson.worldtemps.exporter.CsvExporter;
import io.github.adrianpinson.worldtemps.model.Reading;
import io.github.adrianpinson.worldtemps.service.DashboardQuery;
import io.github.adrianpinson.worldtemps.service.DashboardQuery.Filter;
import io.github.adrianpinson.worldtemps.service.DashboardQuery.Location;
import io.github.adrianpinson.worldtemps.service.DashboardQuery.Result;
import io.github.adrianpinson.worldtemps.service.TemperatureAnalysis;
import io.github.adrianpinson.worldtemps.service.TemperatureAnalysis.Resolution;
import io.github.adrianpinson.worldtemps.service.TemperatureData;
import io.github.adrianpinson.worldtemps.weather.OpenMeteoClient;
import io.github.adrianpinson.worldtemps.weather.WeatherLocation;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.stage.FileChooser;
import javafx.util.StringConverter;

/** Desktop presentation layer. Filtering and summary calculations live in DashboardQuery. */
public final class DashboardView extends BorderPane implements AutoCloseable {
    private static final String ALL_REGIONS = "All regions";
    private static final String ALL_COUNTRIES = "All countries";
    private static final DateTimeFormatter SHORT_DATE = DateTimeFormatter.ofPattern("MMM d, uuuu", Locale.US);
    private static final DateTimeFormatter TICK_DATE = DateTimeFormatter.ofPattern("MMM d", Locale.US);
    private static final int MAX_CHART_POINTS = 1000;

    private final ComboBox<String> region = new ComboBox<>();
    private final ComboBox<String> country = new ComboBox<>();
    private final ComboBox<Location> city = new ComboBox<>();
    private final ComboBox<Location> comparisonA = new ComboBox<>();
    private final ComboBox<Location> comparisonB = new ComboBox<>();
    private final ComboBox<Resolution> resolution = new ComboBox<>();
    private final Button compare = new Button("Compare cities");
    private final HBox comparisonControls = new HBox(10);
    private final MenuButton exportCsv = new MenuButton("Export CSV");
    private final MenuItem exportReadings = new MenuItem("Filtered readings…");
    private final MenuItem exportChart = new MenuItem("Chart data…");
    private final DatePicker from = new DatePicker();
    private final DatePicker to = new DatePicker();
    private final Button apply = new Button("Apply filters");
    private final Button reset = new Button("Reset filters");
    private final Button importCsv = new Button("↑  Import CSV");
    private final Button demo = new Button("Offline demo");
    private final Button findWeather = new Button("Find weather");
    private final Label attribution = new Label("Weather: Open-Meteo / ECMWF ERA5 (CC BY 4.0) · Location data: GeoNames");
    private final Button details = new Button("Import details");
    private final Label feedback = new Label();
    private final Label source = new Label("No dataset loaded");
    private final Label sourceHint = new Label("Import a CSV or explore the sample.");
    private final Label sourceBadge = new Label("NO DATA");
    private final Label status = new Label("Ready to explore");
    private final Label selectionLabel = new Label("Choose a location to get started");
    private final Label chartSubtitle = new Label("Select a city to view its temperature history.");
    private final Label chartNote = new Label("Each point is a source reading. No aggregation is applied.");
    private final Label tableCount = new Label("0 readings");
    private final Label averageValue = new Label("—");
    private final Label minimumValue = new Label("—");
    private final Label maximumValue = new Label("—");
    private final Label countValue = new Label("0");
    private final Label averageDetail = new Label("No matching data");
    private final Label minimumDetail = new Label("No matching data");
    private final Label maximumDetail = new Label("No matching data");
    private final Label countDetail = new Label("No locations selected");
    private final ProgressIndicator progress = new ProgressIndicator();
    private final NumberAxis dateAxis = new NumberAxis();
    private final LineChart<Number, Number> chart = new LineChart<>(dateAxis, new NumberAxis());
    private final TableView<Reading> table = new TableView<>();
    private final Label chartEmpty = new Label("Select a city to view its trend.");
    private final VBox filterContent = new VBox(13);
    private final ExecutorService imports = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "worldtemps-background-work");
        thread.setDaemon(true);
        return thread;
    });
    private DashboardQuery query;
    private ImportResult lastImport;
    private OpenMeteoClient.Dataset lastWeather;
    private final OpenMeteoClient weatherClient;
    private Result currentResult;
    private List<Location> selectedCities = List.of();
    private List<TemperatureAnalysis.Series> currentSeries = List.of();
    private boolean updating;
    private boolean busy;
    private boolean closed;

    public DashboardView() {
        this(new OpenMeteoClient());
    }

    public DashboardView(OpenMeteoClient weatherClient) {
        this.weatherClient = Objects.requireNonNull(weatherClient);
        getStyleClass().add("dashboard");
        setLeft(sidebar());
        setCenter(workspace());
        wireControls();
    }

    private VBox sidebar() {
        VBox sidebar = new VBox(20);
        sidebar.getStyleClass().add("sidebar");
        sidebar.setPrefWidth(236);
        sidebar.setMinWidth(236);
        sidebar.setMaxWidth(236);
        HBox brand = new HBox(10, globe(), label("WorldTemps", "brand"));
        brand.setAlignment(Pos.CENTER_LEFT);
        VBox brandBlock = new VBox(8, brand, label("A WORLD OF TEMPERATURE DATA", "brand-caption"));
        Label active = label("↗   Temperature explorer", "nav-active");
        active.setMaxWidth(Double.MAX_VALUE);
        region.setId("region-filter");
        country.setId("country-filter");
        city.setId("city-filter");
        city.setPromptText("All cities");
        from.setId("from-date");
        to.setId("to-date");
        for (ComboBox<?> box : List.of(region, country, city)) {
            box.setMaxWidth(Double.MAX_VALUE);
        }
        city.setCellFactory(list -> locationCell());
        city.setButtonCell(locationCell());
        for (DatePicker picker : List.of(from, to)) {
            picker.setMaxWidth(Double.MAX_VALUE);
            picker.setConverter(new StringConverter<>() {
                @Override public String toString(LocalDate date) {
                    return date == null ? "" : SHORT_DATE.format(date);
                }
                @Override public LocalDate fromString(String text) {
                    return text == null || text.isBlank() ? null : LocalDate.parse(text.trim(), SHORT_DATE);
                }
            });
            picker.setPromptText("Any date");
        }
        apply.setId("apply-filters");
        apply.getStyleClass().add("accent-button");
        reset.setId("reset-filters");
        reset.getStyleClass().add("sidebar-link");
        apply.setMaxWidth(Double.MAX_VALUE);
        reset.setMaxWidth(Double.MAX_VALUE);
        feedback.setId("filter-feedback");
        feedback.setWrapText(true);
        feedback.getStyleClass().add("feedback");
        feedback.setVisible(false);
        feedback.managedProperty().bind(feedback.visibleProperty());
        filterContent.getChildren().addAll(label("REFINE YOUR VIEW", "eyebrow-light"),
                field("Region", region), field("Country", country), field("City", city),
                field("From", from), field("To", to), apply, reset, feedback);
        ScrollPane filterScroll = new ScrollPane(filterContent);
        filterScroll.getStyleClass().add("filter-scroll");
        filterScroll.setFitToWidth(true);
        filterScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        VBox.setVgrow(filterScroll, Priority.ALWAYS);
        source.getStyleClass().add("source-title");
        source.setWrapText(true);
        sourceHint.getStyleClass().add("source-hint");
        sourceHint.setWrapText(true);
        details.getStyleClass().add("sidebar-link");
        details.setDisable(true);
        details.setMaxWidth(Double.MAX_VALUE);
        VBox sourcePanel = new VBox(8, label("DATA SOURCE", "eyebrow-light"), source, sourceHint, details);
        sourcePanel.getStyleClass().add("source-panel");
        sidebar.getChildren().addAll(brandBlock, active, filterScroll, sourcePanel);
        filterContent.setDisable(true);
        return sidebar;
    }

    private ScrollPane workspace() {
        VBox main = new VBox(20);
        main.setPadding(new Insets(26, 30, 20, 30));
        main.getStyleClass().add("workspace");
        VBox titles = new VBox(6, label("WORKSPACE  /  EXPLORER", "eyebrow"),
                label("Temperature explorer", "page-title"), selectionLabel);
        selectionLabel.getStyleClass().add("muted");
        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);
        sourceBadge.getStyleClass().add("source-badge");
        sourceBadge.setId("source-badge");
        sourceHint.setId("source-hint");
        demo.getStyleClass().add("secondary-button");
        demo.setId("load-demo");
        importCsv.getStyleClass().add("secondary-button");
        importCsv.setId("import-csv");
        findWeather.setId("find-weather");
        findWeather.getStyleClass().add("primary-button");
        progress.setPrefSize(20, 20);
        progress.setMaxSize(20, 20);
        progress.setVisible(false);
        progress.managedProperty().bind(progress.visibleProperty());
        HBox actions = new HBox(8, progress, sourceBadge, demo, importCsv, findWeather);
        actions.setAlignment(Pos.CENTER_RIGHT);
        HBox header = new HBox(16, titles, headerSpacer, actions);
        header.setAlignment(Pos.CENTER_LEFT);

        averageValue.setId("average-value");
        minimumValue.setId("minimum-value");
        maximumValue.setId("maximum-value");
        countValue.setId("count-value");
        HBox cards = new HBox(14,
                metric("AVERAGE TEMPERATURE", averageValue, averageDetail, true),
                metric("LOWEST READING", minimumValue, minimumDetail, false),
                metric("HIGHEST READING", maximumValue, maximumDetail, false),
                metric("VALID READINGS", countValue, countDetail, false));
        chart.setId("temperature-chart");
        chart.setLegendVisible(false);
        chart.setAnimated(false);
        chart.setCreateSymbols(true);
        chart.setHorizontalZeroLineVisible(false);
        chart.setVerticalZeroLineVisible(false);
        chart.setVerticalGridLinesVisible(false);
        chart.setMinHeight(210);
        chart.setPrefHeight(225);
        dateAxis.setForceZeroInRange(false);
        dateAxis.setAnimated(false);
        dateAxis.setTickLabelFormatter(new StringConverter<>() {
            @Override public String toString(Number number) {
                if ((resolution.getValue() == Resolution.YEARLY || resolution.getValue() == Resolution.MONTHLY)
                        && Math.abs(number.doubleValue() - Math.rint(number.doubleValue())) > 0.000001) {
                    return "";
                }
                if (resolution.getValue() == Resolution.YEARLY) {
                    return Integer.toString(number.intValue());
                }
                if (resolution.getValue() == Resolution.MONTHLY) {
                    long month = number.longValue();
                    LocalDate date = LocalDate.of((int) Math.floorDiv(month, 12), (int) Math.floorMod(month, 12) + 1, 1);
                    return DateTimeFormatter.ofPattern("MMM uuuu", Locale.US).format(date);
                }
                LocalDate date = LocalDate.ofEpochDay(number.longValue());
                if (dateAxis.getUpperBound() - dateAxis.getLowerBound() > 366) {
                    return DateTimeFormatter.ofPattern("MMM uuuu", Locale.US).format(date);
                }
                return TICK_DATE.format(date);
            }
            @Override public Number fromString(String text) {
                throw new UnsupportedOperationException("Chart axis is read-only");
            }
        });
        NumberAxis temperatureAxis = (NumberAxis) chart.getYAxis();
        temperatureAxis.setForceZeroInRange(false);
        temperatureAxis.setAnimated(false);
        temperatureAxis.setLabel("Temperature (°F)");
        VBox chartHeading = new VBox(4, label("Temperature over time", "section-title"), chartSubtitle);
        chartSubtitle.getStyleClass().add("muted");
        resolution.setId("chart-resolution");
        resolution.getItems().setAll(Resolution.values());
        resolution.setValue(Resolution.RAW);
        resolution.getStyleClass().add("analysis-select");
        resolution.setPrefWidth(170);
        resolution.setAccessibleText("Chart aggregation");
        Region chartSpacer = new Region();
        HBox.setHgrow(chartSpacer, Priority.ALWAYS);
        HBox chartHeader = new HBox(12, chartHeading, chartSpacer, resolution);
        chartHeader.setAlignment(Pos.CENTER_LEFT);
        comparisonA.setId("comparison-a");
        comparisonB.setId("comparison-b");
        int comparisonNumber = 1;
        for (ComboBox<Location> box : List.of(comparisonA, comparisonB)) {
            box.setCellFactory(list -> comparisonCell());
            box.setButtonCell(comparisonCell());
            box.setPromptText("No comparison");
            box.setAccessibleText("Comparison city " + comparisonNumber++);
            box.getStyleClass().add("analysis-select");
            box.setPrefWidth(220);
            box.setMinWidth(145);
            HBox.setHgrow(box, Priority.ALWAYS);
        }
        compare.setId("compare-cities");
        compare.getStyleClass().add("secondary-button");
        comparisonControls.getChildren().addAll(label("Compare with", "muted"), comparisonA, comparisonB, compare);
        comparisonControls.setAlignment(Pos.CENTER_LEFT);
        comparisonControls.setDisable(true);
        chartEmpty.setId("chart-empty");
        chartEmpty.getStyleClass().add("empty-message");
        chartEmpty.setWrapText(true);
        chart.setVisible(false);
        StackPane plot = new StackPane(chart, chartEmpty);
        chartNote.getStyleClass().add("caption");
        chartNote.setWrapText(true);
        VBox chartCard = new VBox(8, chartHeader, comparisonControls, plot, chartNote);
        chartCard.getStyleClass().add("surface");

        buildTable();
        Region tableSpacer = new Region();
        HBox.setHgrow(tableSpacer, Priority.ALWAYS);
        tableCount.setId("table-count");
        tableCount.getStyleClass().add("subtle-badge");
        exportCsv.setId("export-csv");
        exportCsv.getStyleClass().add("export-button");
        exportCsv.getItems().addAll(exportReadings, exportChart);
        exportCsv.setDisable(true);
        HBox tableHeader = new HBox(12, label("Explore the readings", "section-title"), tableSpacer, tableCount, exportCsv);
        tableHeader.setAlignment(Pos.CENTER_LEFT);
        VBox tableCard = new VBox(12, tableHeader, table, label("Click a column heading to sort. All temperatures are in Fahrenheit.", "caption"));
        tableCard.getStyleClass().add("surface");
        status.setId("status-label");
        status.getStyleClass().add("caption");
        attribution.getStyleClass().add("caption");
        attribution.setWrapText(true);
        attribution.setVisible(false);
        attribution.managedProperty().bind(attribution.visibleProperty());
        main.getChildren().addAll(header, cards, chartCard, tableCard, status, attribution);
        ScrollPane scroll = new ScrollPane(main);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.getStyleClass().add("main-scroll");
        return scroll;
    }

    private void buildTable() {
        table.setId("readings-table");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setFixedCellSize(35);
        table.setPrefHeight(210);
        table.setMinHeight(175);
        table.setPlaceholder(label("No readings match these filters. Try a wider date range.", "empty-message"));
        TableColumn<Reading, LocalDate> date = column("Date", Reading::date, 120);
        table.getColumns().add(date);
        table.getColumns().add(column("City", Reading::city, 150));
        table.getColumns().add(column("State", r -> r.state().isBlank() ? "—" : r.state(), 110));
        table.getColumns().add(column("Country", Reading::country, 110));
        table.getColumns().add(column("Region", Reading::region, 160));
        TableColumn<Reading, Double> temperature = column("Temperature", Reading::averageTemperatureF, 140);
        temperature.setCellFactory(c -> new TableCell<>() {
            @Override protected void updateItem(Double value, boolean empty) {
                super.updateItem(value, empty);
                setText(empty || value == null ? null : String.format(Locale.US, "%.1f °F", value));
                getStyleClass().remove("temperature-cell");
                getStyleClass().add("temperature-cell");
            }
        });
        table.getColumns().add(temperature);
        table.getSortOrder().add(date);
    }

    private static <T> TableColumn<Reading, T> column(String title, Function<Reading, T> value, double width) {
        TableColumn<Reading, T> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(value.apply(cell.getValue())));
        column.setPrefWidth(width);
        column.setMinWidth(75);
        return column;
    }

    private void wireControls() {
        region.setOnAction(e -> {
            if (!updating && query != null) {
                updating = true;
                String previous = selectedCountry();
                country.getItems().setAll(query.countries(selectedRegion()));
                country.getItems().add(0, ALL_COUNTRIES);
                country.setValue(previous != null && country.getItems().contains(previous) ? previous : ALL_COUNTRIES);
                updateLocations();
                updating = false;
            }
        });
        country.setOnAction(e -> {
            if (!updating && query != null) {
                updating = true;
                updateLocations();
                updating = false;
            }
        });
        apply.setOnAction(e -> applyFilters());
        compare.setOnAction(e -> applyFilters());
        city.setOnAction(e -> {
            if (!updating) {
                if (city.getValue() == null) {
                    comparisonA.setValue(null);
                    comparisonB.setValue(null);
                }
                comparisonControls.setDisable(busy || city.getValue() == null);
            }
        });
        resolution.setOnAction(e -> {
            if (currentResult != null && !updating) {
                renderChart(currentResult.readings(), selectedCities);
            }
        });
        exportReadings.setOnAction(e -> chooseExport(false));
        exportChart.setOnAction(e -> chooseExport(true));
        reset.setOnAction(e -> resetFilters(false));
        demo.setOnAction(e -> loadDemo());
        findWeather.setOnAction(e -> openWeatherLoader());
        importCsv.setOnAction(e -> {
            FileChooser picker = new FileChooser();
            picker.setTitle("Import temperature data");
            picker.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV files", "*.csv", "*.CSV"));
            var file = picker.showOpenDialog(getScene().getWindow());
            if (file != null) {
                importFile(file.toPath());
            }
        });
        details.setOnAction(e -> showImportDetails());
    }

    private void updateLocations() {
        Location previous = city.getValue();
        city.getItems().setAll(query.locations(selectedRegion(), selectedCountry()));
        city.getItems().add(0, null);
        city.setValue(previous != null && city.getItems().contains(previous) ? previous : null);
        for (ComboBox<Location> box : List.of(comparisonA, comparisonB)) {
            Location selected = box.getValue();
            box.getItems().setAll(query.locations(selectedRegion(), selectedCountry()));
            box.getItems().add(0, null);
            box.setValue(city.getValue() != null && box.getItems().contains(selected) ? selected : null);
        }
        comparisonControls.setDisable(busy || city.getValue() == null);
    }

    private String selectedRegion() {
        return ALL_REGIONS.equals(region.getValue()) ? null : region.getValue();
    }

    private String selectedCountry() {
        return ALL_COUNTRIES.equals(country.getValue()) ? null : country.getValue();
    }

    private void resetFilters(boolean selectFirstCity) {
        if (query == null) {
            return;
        }
        updating = true;
        region.getItems().setAll(query.regions());
        region.getItems().add(0, ALL_REGIONS);
        region.setValue(ALL_REGIONS);
        country.getItems().setAll(query.countries(null));
        country.getItems().add(0, ALL_COUNTRIES);
        country.setValue(ALL_COUNTRIES);
        city.setValue(null);
        comparisonA.setValue(null);
        comparisonB.setValue(null);
        resolution.setValue(Resolution.RAW);
        updateLocations();
        if (selectFirstCity && city.getItems().size() > 1) {
            Location initial = city.getItems().stream().filter(Objects::nonNull)
                    .filter(location -> location.city().equals("Longview")).findFirst()
                    .orElse(city.getItems().get(1));
            city.setValue(initial);
        }
        comparisonControls.setDisable(busy || city.getValue() == null);
        from.setValue(query.firstDate().orElse(null));
        to.setValue(query.lastDate().orElse(null));
        from.getEditor().setText(from.getConverter().toString(from.getValue()));
        to.getEditor().setText(to.getConverter().toString(to.getValue()));
        updating = false;
        applyFilters();
    }

    private void applyFilters() {
        if (query == null) {
            return;
        }
        try {
            from.commitValue();
            to.commitValue();
            Filter filter = new Filter(selectedRegion(), selectedCountry(), city.getValue(), from.getValue(), to.getValue());
            List<Location> comparisons = java.util.stream.Stream.of(comparisonA.getValue(), comparisonB.getValue())
                    .filter(Objects::nonNull).distinct().toList();
            Result result = query.apply(filter, comparisons);
            selectedCities = java.util.stream.Stream.concat(java.util.stream.Stream.ofNullable(filter.location()),
                    comparisons.stream()).distinct().toList();
            currentResult = result;
            feedback.setVisible(false);
            render(result, filter.location());
        } catch (IllegalArgumentException | java.time.DateTimeException e) {
            error(e instanceof java.time.DateTimeException
                    ? "Enter a valid date, such as Jan 1, 2025." : e.getMessage());
        }
    }

    private void render(Result result, Location location) {
        table.getItems().setAll(result.readings());
        table.sort();
        tableCount.setText(String.format(Locale.US, "%,d matching readings", result.readings().size()));
        selectionLabel.setText(selectedCities.size() > 1 ? selectedCities.size() + " cities compared · Shared date range · Fahrenheit"
                : location == null ? "All matching locations · Fahrenheit" : location + " · Fahrenheit");
        var summary = result.summary();
        averageValue.setText(summary.map(s -> degrees(s.average())).orElse("—"));
        minimumValue.setText(summary.map(s -> degrees(s.minimum())).orElse("—"));
        maximumValue.setText(summary.map(s -> degrees(s.maximum())).orElse("—"));
        countValue.setText(String.format(Locale.US, "%,d", result.readings().size()));
        averageDetail.setText(summary.isPresent() ? "Mean of matching readings" : "No matching data");
        minimumDetail.setText(extremeDetail(result.readings(), true));
        maximumDetail.setText(extremeDetail(result.readings(), false));
        countDetail.setText(summary.map(s -> s.cities() + (s.cities() == 1 ? " city" : " cities") + " in this selection")
                .orElse("No matching locations"));
        renderChart(result.readings(), selectedCities);
    }

    private void renderChart(List<Reading> readings, List<Location> locations) {
        chart.getData().clear();
        Resolution level = resolution.getValue();
        currentSeries = locations.isEmpty() ? List.of() : TemperatureAnalysis.analyze(readings, level).stream()
                .sorted(java.util.Comparator.comparingInt(series -> locations.indexOf(series.location()))).toList();
        boolean visible = !currentSeries.isEmpty();
        chart.setVisible(visible);
        chart.setLegendVisible(currentSeries.size() > 1);
        chartEmpty.setVisible(!visible);
        chartSubtitle.setText(locations.isEmpty() ? "Choose a city in the filters to see its trend."
                : locations.size() == 1 ? locations.get(0).toString() : locations.size() + " cities · " + level);
        chartEmpty.setText(locations.isEmpty() ? "Select a city to view its trend.\nThe table and summaries show all matching locations."
                : "No readings in this date range.\nTry widening your filters.");
        chartNote.setText(level == Resolution.RAW
                ? "Each point is a source reading. Lines connect observations; missing dates are not filled."
                : "Period means use only filtered source readings, including duplicates. Cards and table stay based on raw readings.");
        refreshExportState();
        if (!visible) {
            return;
        }
        long start = currentSeries.stream().flatMap(series -> series.points().stream())
                .mapToLong(point -> chartPosition(point.start(), level)).min().orElseThrow();
        long end = currentSeries.stream().flatMap(series -> series.points().stream())
                .mapToLong(point -> chartPosition(point.start(), level)).max().orElseThrow();
        dateAxis.setAutoRanging(false);
        double padding = level == Resolution.MONTHLY || level == Resolution.YEARLY ? (start == end ? 1 : 0) : 1;
        dateAxis.setLowerBound(start - padding);
        dateAxis.setUpperBound(end + padding);
        dateAxis.setTickUnit(Math.max(1, Math.ceil((end - start) / 6.0)));
        boolean limited = false;
        for (TemperatureAnalysis.Series citySeries : currentSeries) {
            var points = citySeries.points();
            var plotted = points.subList(0, Math.min(points.size(), MAX_CHART_POINTS));
            limited |= points.size() > MAX_CHART_POINTS;
            XYChart.Series<Number, Number> series = new XYChart.Series<>();
            series.setName(citySeries.location().toString());
            for (var value : plotted) {
                XYChart.Data<Number, Number> point = new XYChart.Data<>(chartPosition(value.start(), level), value.average());
                String text = String.format(Locale.US, "%s%n%s to %s%nMean: %.1f °F · Min: %.1f °F · Max: %.1f °F%n%d source readings",
                        citySeries.location(), value.start(), value.end(), value.average(), value.minimum(), value.maximum(), value.count());
                point.nodeProperty().addListener((observable, oldNode, node) -> {
                    if (node != null) {
                        node.setAccessibleHelp(text);
                        Tooltip.install(node, new Tooltip(text));
                    }
                });
                series.getData().add(point);
            }
            chart.getData().add(series);
        }
        if (limited) {
            chartNote.setText("Plot limited to the first 1,000 points per city. Narrow the dates for later points; exports include all matching data.");
        }
    }

    private void refreshExportState() {
        exportReadings.setDisable(currentResult == null || currentResult.readings().isEmpty());
        exportChart.setDisable(currentSeries.isEmpty());
        exportCsv.setDisable(busy || currentResult == null || currentResult.readings().isEmpty());
    }

    private static long chartPosition(LocalDate date, Resolution level) {
        return switch (level) {
            case RAW, DAILY -> date.toEpochDay();
            case MONTHLY -> (long) date.getYear() * 12 + date.getMonthValue() - 1;
            case YEARLY -> date.getYear();
        };
    }

    private void chooseExport(boolean chartData) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(chartData ? "Export chart data" : "Export filtered readings");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV files", "*.csv"));
        chooser.setInitialFileName(chartData ? "worldtemps-" + resolution.getValue().name().toLowerCase(Locale.ROOT) + ".csv"
                : "worldtemps-readings.csv");
        var file = chooser.showSaveDialog(getScene().getWindow());
        if (file != null) {
            if (chartData) {
                exportChartData(file.toPath());
            } else {
                exportReadings(file.toPath());
            }
        }
    }

    public CompletableFuture<Void> exportReadings(Path path) {
        List<Reading> snapshot = List.copyOf(table.getItems());
        if (snapshot.isEmpty()) {
            return CompletableFuture.failedFuture(new IllegalStateException("No readings to export."));
        }
        return export(path, () -> CsvExporter.writeReadings(path, snapshot));
    }

    public CompletableFuture<Void> exportChartData(Path path) {
        List<TemperatureAnalysis.Series> snapshot = currentSeries;
        if (snapshot.isEmpty()) {
            return CompletableFuture.failedFuture(new IllegalStateException("Select a city with matching data before exporting the chart."));
        }
        return export(path, () -> CsvExporter.writeChart(path, snapshot));
    }

    private CompletableFuture<Void> export(Path path, ExportAction action) {
        if (closed || busy) {
            return CompletableFuture.failedFuture(new IllegalStateException("Another operation is running or the dashboard is closed."));
        }
        CompletableFuture<Void> completion = new CompletableFuture<>();
        setBusy(true);
        feedback.setVisible(false);
        status.setText("Exporting " + path.getFileName() + "…");
        Task<Void> task = new Task<>() {
            @Override protected Void call() throws IOException {
                action.write();
                return null;
            }
        };
        task.setOnSucceeded(e -> {
            setBusy(false);
            status.setText("Exported " + path.getFileName() + " · Fahrenheit · Current filtered view");
            completion.complete(null);
        });
        task.setOnFailed(e -> {
            setBusy(false);
            error("Export failed: " + Objects.toString(task.getException().getMessage(), "Choose another location."));
            status.setText("Export failed · dashboard data retained");
            completion.completeExceptionally(task.getException());
        });
        imports.submit(task);
        return completion;
    }

    @FunctionalInterface private interface ExportAction {
        void write() throws IOException;
    }

    private static String extremeDetail(List<Reading> readings, boolean minimum) {
        var comparator = java.util.Comparator.comparingDouble(Reading::averageTemperatureF);
        return (minimum ? readings.stream().min(comparator) : readings.stream().max(comparator))
                .map(r -> TICK_DATE.format(r.date()) + " · " + r.city()).orElse("No matching data");
    }

    public CompletableFuture<Void> loadDemo() {
        return load(() -> new InputStreamReader(Objects.requireNonNull(
                getClass().getResourceAsStream("/data/sample-temperatures.csv")), StandardCharsets.UTF_8),
                "WorldTemps sample", true);
    }

    public CompletableFuture<Void> importFile(Path path) {
        return load(() -> Files.newBufferedReader(path, StandardCharsets.UTF_8), path.getFileName().toString(), false);
    }

    public void openWeatherLoader() {
        if (busy || closed) { return; }
        new WeatherLoadDialog(weatherClient, getScene().getWindow()).showAndWait()
                .ifPresent(request -> loadWeather(request.cities(), request.start(), request.end()));
    }

    public CompletableFuture<Void> loadWeather(List<WeatherLocation> locations, LocalDate start, LocalDate end) {
        if (busy || closed) {
            return CompletableFuture.failedFuture(new IllegalStateException("Another operation is running or the dashboard is closed."));
        }
        List<WeatherLocation> selection = List.copyOf(locations);
        CompletableFuture<Void> completion = new CompletableFuture<>();
        setBusy(true);
        feedback.setVisible(false);
        status.setText("Loading historical weather…");
        Task<OpenMeteoClient.Dataset> task = new Task<>() {
            @Override protected OpenMeteoClient.Dataset call() throws Exception {
                return weatherClient.load(selection, start, end);
            }
        };
        task.setOnSucceeded(e -> {
            setBusy(false);
            if (closed) { completion.cancel(false); return; }
            lastWeather = task.getValue();
            lastImport = null;
            query = new DashboardQuery(new TemperatureData(lastWeather.readings()));
            filterContent.setDisable(false);
            source.setText("Open-Meteo / ERA5");
            long offline = lastWeather.cities().stream().filter(c -> c.origin() == OpenMeteoClient.Origin.OFFLINE_CACHE).count();
            long cached = lastWeather.cities().stream().filter(c -> c.origin() != OpenMeteoClient.Origin.NETWORK).count();
            sourceHint.setText("Reanalysis · daily means · " + cached + " cached cities"
                    + (offline > 0 ? " · Offline cache may be out of date" : ""));
            sourceBadge.setText(offline > 0 ? "OFFLINE CACHE" : "WEATHER API");
            sourceBadge.getStyleClass().remove("imported-badge");
            sourceBadge.getStyleClass().add("imported-badge");
            attribution.setVisible(true);
            details.setText("Source details");
            details.setDisable(false);
            status.setText(String.format(Locale.US, "Loaded %,d daily means · %,d unavailable days · %d cities · Fahrenheit%s",
                    lastWeather.readings().size(), lastWeather.missingDays(), lastWeather.cities().size(),
                    offline > 0 ? " · Offline cached data" : ""));
            resetFilters(true);
            List<Location> labels = lastWeather.cities().stream().map(c -> new Location(c.location().country(),
                    c.location().state(), c.location().city())).toList();
            city.setValue(labels.get(0));
            comparisonA.setValue(labels.size() > 1 ? labels.get(1) : null);
            comparisonB.setValue(labels.size() > 2 ? labels.get(2) : null);
            from.setValue(start);
            to.setValue(end);
            applyFilters();
            completion.complete(null);
        });
        task.setOnFailed(e -> {
            setBusy(false);
            if (!closed) {
                error("Weather load failed: " + Objects.toString(task.getException().getMessage(), "Try again later."));
                status.setText(query == null ? "No dataset loaded · Try Offline demo" : "Weather load failed · previous dataset retained");
            }
            completion.completeExceptionally(task.getException());
        });
        imports.submit(task);
        return completion;
    }

    private CompletableFuture<Void> load(ReaderSource reader, String name, boolean synthetic) {
        if (closed || busy) {
            return CompletableFuture.failedFuture(new IllegalStateException("An import is already running or the dashboard is closed."));
        }
        CompletableFuture<Void> completion = new CompletableFuture<>();
        setBusy(true);
        feedback.setVisible(false);
        status.setText("Loading " + name + "…");
        Task<ImportResult> task = new Task<>() {
            @Override protected ImportResult call() throws IOException {
                try (Reader input = reader.open()) {
                    ImportResult result = new CsvTemperatureLoader().load(input);
                    if (result.data().size() == 0) {
                        throw new IOException("No valid readings were found. Check the CSV values and try again.");
                    }
                    return result;
                }
            }
        };
        task.setOnSucceeded(e -> {
            setBusy(false);
            if (closed) {
                completion.cancel(false);
                return;
            }
            lastImport = task.getValue();
            lastWeather = null;
            attribution.setVisible(false);
            details.setText("Import details");
            query = new DashboardQuery(lastImport.data());
            filterContent.setDisable(false);
            source.setText(name);
            sourceHint.setText(synthetic ? "Synthetic demo · not historical observations."
                    : String.format(Locale.US, "%,d valid · %,d rejected records", lastImport.data().size(), lastImport.rejectedRows()));
            sourceBadge.setText(synthetic ? "DEMO DATA" : "CSV DATA");
            sourceBadge.getStyleClass().remove("imported-badge");
            if (!synthetic) {
                sourceBadge.getStyleClass().add("imported-badge");
            }
            status.setText(String.format(Locale.US, "Loaded %,d readings · %,d rejected · Fahrenheit%s",
                    lastImport.data().size(), lastImport.rejectedRows(), synthetic ? " · Synthetic demonstration data" : ""));
            details.setDisable(false);
            resetFilters(true);
            completion.complete(null);
        });
        task.setOnFailed(e -> {
            setBusy(false);
            if (!closed) {
                error("Import failed: " + Objects.toString(task.getException().getMessage(), "Check the CSV format."));
                status.setText(query == null ? "No dataset loaded" : "Import failed · previous dataset retained");
            }
            completion.completeExceptionally(task.getException());
        });
        imports.submit(task);
        return completion;
    }

    private void setBusy(boolean value) {
        busy = value;
        importCsv.setDisable(value);
        demo.setDisable(value);
        findWeather.setDisable(value);
        filterContent.setDisable(value || query == null);
        resolution.setDisable(value);
        comparisonControls.setDisable(value || city.getValue() == null);
        refreshExportState();
        progress.setVisible(value);
    }

    private void error(String message) {
        feedback.setText(message);
        feedback.setVisible(true);
    }

    private void showImportDetails() {
        if (lastWeather != null) {
            StringBuilder text = new StringBuilder("Historical daily mean temperatures, not station observations or forecasts.\n"
                    + "Source: Open-Meteo / ECMWF ERA5 reanalysis, CC BY 4.0.\n"
                    + "https://open-meteo.com/en/docs/historical-weather-api\n"
                    + "Locations: GeoNames via Open-Meteo. https://www.geonames.org/\n\n");
            lastWeather.cities().forEach(city -> text.append(city.location()).append('\n')
                    .append("Coordinates: ").append(city.location().latitude()).append(", ").append(city.location().longitude())
                    .append("\nTimezone: ").append(city.location().timezone()).append("\nOrigin: ").append(city.origin())
                    .append("\nFetched: ").append(city.fetchedAt()).append("\nUnavailable days: ").append(city.missingDays())
                    .append(city.cacheSaved() ? "" : "\nCould not save a local cache copy.").append("\n\n"));
            showDetails("Weather source details", text.toString());
            return;
        }
        if (lastImport == null) {
            return;
        }
        StringBuilder text = new StringBuilder(String.format(Locale.US, "Accepted: %,d%nRejected: %,d%n%n",
                lastImport.data().size(), lastImport.rejectedRows()));
        lastImport.issues().forEach(issue -> text.append("Record ").append(issue.recordNumber()).append(": ")
                .append(issue.reason()).append('\n'));
        if (lastImport.rejectedRows() > lastImport.issues().size()) {
            text.append("\nOnly the first 20 rejected-record diagnostics are shown.");
        }
        if (lastImport.rejectedRows() == 0) {
            text.append("All records passed import validation.");
        }
        showDetails("Import details", text.toString());
    }

    private void showDetails(String title, String text) {
        TextArea content = new TextArea(text);
        content.setEditable(false);
        content.setWrapText(true);
        content.setPrefSize(520, 280);
        Dialog<Void> dialog = new Dialog<>();
        dialog.initOwner(getScene().getWindow());
        dialog.setTitle(title);
        dialog.setHeaderText(source.getText());
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.showAndWait();
    }

    private static ListCell<Location> comparisonCell() {
        return locationCell("No comparison");
    }

    private static ListCell<Location> locationCell() {
        return locationCell("All cities");
    }

    private static ListCell<Location> locationCell(String emptyLabel) {
        return new ListCell<>() {
            @Override protected void updateItem(Location location, boolean empty) {
                super.updateItem(location, empty);
                setText(empty ? null : location == null ? emptyLabel : location.toString());
            }
        };
    }

    private static VBox field(String name, Control control) {
        Label caption = label(name, "filter-label");
        caption.setLabelFor(control);
        return new VBox(6, caption, control);
    }

    private static VBox metric(String title, Label value, Label description, boolean highlighted) {
        value.getStyleClass().add("metric-value");
        description.getStyleClass().add("metric-detail");
        value.setMinWidth(0);
        description.setMinWidth(0);
        VBox card = new VBox(9, label(title, "metric-title"), value, description);
        card.getStyleClass().add("metric-card");
        if (highlighted) {
            card.getStyleClass().add("metric-highlight");
        }
        card.setMinWidth(0);
        card.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(card, Priority.ALWAYS);
        card.setPrefWidth(220);
        return card;
    }

    private static Label label(String text, String style) {
        Label label = new Label(text);
        label.getStyleClass().add(style);
        return label;
    }

    private static String degrees(double value) {
        return String.format(Locale.US, "%.1f°", value);
    }

    private static Pane globe() {
        Circle disc = new Circle(18, 18, 18, Color.web("#f6a45d"));
        Circle outline = new Circle(18, 18, 10, Color.TRANSPARENT);
        outline.setStroke(Color.web("#172b3b"));
        outline.setStrokeWidth(1.4);
        Line horizontal = new Line(8, 18, 28, 18);
        horizontal.setStroke(Color.web("#172b3b"));
        javafx.scene.shape.Ellipse meridian = new javafx.scene.shape.Ellipse(18, 18, 4, 10);
        meridian.setFill(Color.TRANSPARENT);
        meridian.setStroke(Color.web("#172b3b"));
        Pane icon = new Pane(disc, outline, horizontal, meridian);
        icon.setMinSize(36, 36);
        icon.setMaxSize(36, 36);
        return icon;
    }

    @Override public void close() {
        closed = true;
        imports.shutdownNow();
    }

    @FunctionalInterface private interface ReaderSource {
        Reader open() throws IOException;
    }
}
