package io.github.adrianpinson.worldtemps.ui;

import javafx.application.Application;

/** Separate launcher avoids Java's special handling of Application subclasses on the classpath. */
public final class DesktopLauncher {
    private DesktopLauncher() { }

    public static void main(String[] args) {
        if (args.length > 0 && args[0].equals("--smoke-test")) {
            try {
                PackageSmoke.verify();
                String result = "WorldTemps packaged runtime: OK\njava.home=" + System.getProperty("java.home") + "\n";
                if (args.length == 3 && args[1].equals("--report")) {
                    java.nio.file.Files.writeString(java.nio.file.Path.of(args[2]), result);
                }
                System.out.print(result);
                return;
            } catch (Exception error) {
                throw new IllegalStateException("WorldTemps package smoke check failed", error);
            }
        }
        if (args.length == 1 && args[0].equals("--version")) {
            System.out.println("WorldTemps " + java.util.Objects.toString(DesktopLauncher.class.getPackage().getImplementationVersion(), "development"));
            return;
        }
        Application.launch(TemperatureDashboard.class, args);
    }
}
