package io.github.adrianpinson.worldtemps.ui;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class PackageSmokeTest {
    @TempDir Path directory;
    @Test void verifiesBundledResourcesAndWritesAHeadlessReportWithoutLaunchingJavaFx() throws Exception {
        Path report = directory.resolve("smoke.txt");
        DesktopLauncher.main(new String[] {"--smoke-test", "--report", report.toString()});
        assertTrue(Files.readString(report).startsWith("WorldTemps packaged runtime: OK\njava.home="));
    }
}
