package io.github.adrianpinson.worldtemps.ui;

import io.github.adrianpinson.worldtemps.weather.OpenMeteoClient;
import io.github.adrianpinson.worldtemps.weather.WeatherLocation;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Window;

/** Explicit city selection and dates before any archive request is made. */
public final class WeatherLoadDialog extends Dialog<WeatherLoadDialog.Request> {
    private final OpenMeteoClient client;
    private final TextField searchText = new TextField();
    private final Button searchButton = new Button("Search");
    private final ListView<WeatherLocation> results = new ListView<>();
    private final ListView<WeatherLocation> chosen = new ListView<>();
    private final DatePicker start = new DatePicker();
    private final DatePicker end = new DatePicker();
    private final Label message = new Label("Search for a city, then add up to three locations.");
    private final Button add = new Button("Add city");
    private final Button remove = new Button("Remove");
    private final ButtonType loadType = new ButtonType("Load historical weather", ButtonBar.ButtonData.OK_DONE);
    private final ExecutorService searches = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "worldtemps-city-search");
        thread.setDaemon(true);
        return thread;
    });
    private Task<List<WeatherLocation>> activeSearch;
    private boolean searching;

    public WeatherLoadDialog(OpenMeteoClient client, Window owner) {
        this.client = client;
        initOwner(owner);
        setTitle("Find historical weather");
        setHeaderText("Choose your cities. Explore their history.");
        searchText.setId("weather-search-text");
        searchText.setPromptText("City, e.g. Longview, Texas");
        searchText.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(searchText, Priority.ALWAYS);
        searchButton.setId("weather-search");
        searchButton.setOnAction(e -> search(searchText.getText()));
        searchText.setOnAction(e -> search(searchText.getText()));
        results.setId("weather-results");
        chosen.setId("weather-chosen");
        results.setPrefHeight(130);
        chosen.setPrefHeight(92);
        results.setPlaceholder(new Label("Matching locations appear here."));
        chosen.setPlaceholder(new Label("No cities selected."));
        results.setCellFactory(list -> locationCell());
        chosen.setCellFactory(list -> locationCell());
        add.setId("weather-add");
        add.setOnAction(e -> {
            WeatherLocation location = results.getSelectionModel().getSelectedItem();
            if (location == null) { message.setText("Select a search result first."); return; }
            if (chosen.getItems().stream().anyMatch(item -> item.id() == location.id())) {
                message.setText("That location is already selected."); return;
            }
            if (chosen.getItems().size() == 3) { message.setText("You can compare up to three cities."); return; }
            chosen.getItems().add(location);
            message.setText(chosen.getItems().size() + " cities selected. Choose dates, then load the weather.");
            updateLoadState();
        });
        remove.setId("weather-remove");
        remove.setOnAction(e -> {
            chosen.getItems().remove(chosen.getSelectionModel().getSelectedItem());
            updateLoadState();
        });
        start.setId("weather-start");
        end.setId("weather-end");
        end.setValue(client.latestDate());
        start.setValue(end.getValue().minusYears(1).plusDays(1));
        for (DatePicker picker : List.of(start, end)) {
            picker.setMaxWidth(Double.MAX_VALUE);
            picker.setDayCellFactory(calendar -> new DateCell() {
                @Override public void updateItem(LocalDate date, boolean empty) {
                    super.updateItem(date, empty);
                    setDisable(empty || date.isBefore(OpenMeteoClient.EARLIEST) || date.isAfter(client.latestDate()));
                }
            });
        }
        HBox range = new HBox(15, new VBox(5, new Label("From"), start), new VBox(5, new Label("To"), end));
        Label explanation = new Label("Daily mean temperatures · ERA5 reanalysis · Fahrenheit\nArchive available through "
                + client.latestDate() + " · Up to 3,660 days per load. No API key needed.");
        explanation.setWrapText(true);
        explanation.getStyleClass().add("muted");
        message.setId("weather-message");
        message.setWrapText(true);
        VBox content = new VBox(10, new HBox(10, searchText, searchButton), results,
                new HBox(10, add, remove), new Label("SELECTED CITIES"), chosen, range, explanation, message,
                new Label("Weather: Open-Meteo / ECMWF · Location search: GeoNames"));
        content.setPadding(new Insets(8));
        content.setPrefWidth(570);
        getDialogPane().setContent(content);
        getDialogPane().getStylesheets().add(getClass().getResource("/ui/dashboard.css").toExternalForm());
        getDialogPane().getStyleClass().add("weather-dialog");
        getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, loadType);
        getDialogPane().lookupButton(loadType).setId("weather-load");
        getDialogPane().lookupButton(loadType).addEventFilter(ActionEvent.ACTION, e -> {
            try {
                start.commitValue();
                end.commitValue();
                client.validateDates(start.getValue(), end.getValue());
            } catch (RuntimeException error) {
                message.setText("Choose valid dates: " + error.getMessage());
                e.consume();
            }
        });
        setResultConverter(type -> type == loadType ? new Request(List.copyOf(chosen.getItems()), start.getValue(), end.getValue()) : null);
        setOnHidden(e -> {
            if (activeSearch != null) { activeSearch.cancel(true); }
            searches.shutdownNow();
        });
        updateLoadState();
        Platform.runLater(searchText::requestFocus);
    }

    public CompletableFuture<List<WeatherLocation>> search(String name) {
        if (searches.isShutdown() || searching) {
            return CompletableFuture.failedFuture(new IllegalStateException("A search is running or this dialog is closed."));
        }
        CompletableFuture<List<WeatherLocation>> completion = new CompletableFuture<>();
        searching = true;
        searchText.setText(name);
        searchButton.setDisable(true);
        add.setDisable(true);
        results.getItems().clear();
        message.setText("Searching locations…");
        activeSearch = new Task<>() {
            @Override protected List<WeatherLocation> call() throws Exception { return client.search(name); }
        };
        Task<List<WeatherLocation>> task = activeSearch;
        task.setOnSucceeded(e -> {
            searching = false;
            searchButton.setDisable(false);
            add.setDisable(false);
            results.getItems().setAll(task.getValue());
            message.setText(task.getValue().isEmpty() ? "No matching cities. Try a nearby city or include the state/country."
                    : "Select a location, then click Add city. Coordinates distinguish matching names.");
            updateLoadState();
            completion.complete(task.getValue());
        });
        task.setOnFailed(e -> {
            searching = false;
            searchButton.setDisable(false);
            add.setDisable(false);
            message.setText("Search failed: " + task.getException().getMessage());
            updateLoadState();
            completion.completeExceptionally(task.getException());
        });
        task.setOnCancelled(e -> completion.cancel(false));
        searches.submit(task);
        return completion;
    }

    private void updateLoadState() {
        getDialogPane().lookupButton(loadType).setDisable(chosen.getItems().isEmpty());
    }

    private static ListCell<WeatherLocation> locationCell() {
        return new ListCell<>() {
            @Override protected void updateItem(WeatherLocation location, boolean empty) {
                super.updateItem(location, empty);
                setText(empty || location == null ? null : String.format(java.util.Locale.US, "%s  (%.3f, %.3f)",
                        location, location.latitude(), location.longitude()));
            }
        };
    }

    public record Request(List<WeatherLocation> cities, LocalDate start, LocalDate end) { }
}
