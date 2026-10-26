package io.github.adrianpinson.worldtemps.ui;

import javafx.application.Application;

/** Separate launcher avoids Java's special handling of Application subclasses on the classpath. */
public final class DesktopLauncher {
    private DesktopLauncher() { }

    public static void main(String[] args) {
        Application.launch(TemperatureDashboard.class, args);
    }
}
