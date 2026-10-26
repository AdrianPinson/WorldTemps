package io.github.adrianpinson.worldtemps.ui;

import java.nio.file.Path;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

public final class TemperatureDashboard extends Application {
    private DashboardView dashboard;

    @Override public void start(Stage stage) {
        dashboard = new DashboardView();
        Scene scene = new Scene(dashboard, 1440, 1000);
        scene.getStylesheets().add(TemperatureDashboard.class.getResource("/ui/dashboard.css").toExternalForm());
        stage.setTitle("WorldTemps — Temperature explorer");
        stage.setMinWidth(1050);
        stage.setMinHeight(760);
        stage.setScene(scene);
        stage.show();
        var arguments = getParameters().getRaw();
        if (arguments.isEmpty()) {
            dashboard.loadDemo();
        } else {
            dashboard.importFile(Path.of(arguments.get(0)));
        }
    }

    @Override public void stop() {
        if (dashboard != null) {
            dashboard.close();
        }
    }
}
