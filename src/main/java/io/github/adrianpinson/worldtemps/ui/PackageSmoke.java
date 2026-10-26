package io.github.adrianpinson.worldtemps.ui;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.adrianpinson.worldtemps.importer.CsvTemperatureLoader;
import io.github.adrianpinson.worldtemps.service.DashboardQuery;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.Signature;
import java.util.Objects;

/** Headless validation of a packaged runtime; no network or graphical toolkit startup. */
final class PackageSmoke {
    private PackageSmoke() { }
    static void verify() throws Exception {
        Class.forName("javafx.scene.control.Control", false, PackageSmoke.class.getClassLoader());
        Objects.requireNonNull(PackageSmoke.class.getResource("/ui/dashboard.css"), "Missing dashboard stylesheet");
        if (!new ObjectMapper().readTree("{\"ok\":true}").path("ok").asBoolean()) {
            throw new IllegalStateException("JSON support is missing");
        }
        Signature.getInstance("SHA256withECDSA");
        java.net.http.HttpClient.newHttpClient();
        try (var reader = new InputStreamReader(Objects.requireNonNull(PackageSmoke.class.getResourceAsStream("/data/sample-temperatures.csv")), StandardCharsets.UTF_8)) {
            var data = new CsvTemperatureLoader().load(reader).data();
            if (data.size() == 0 || new DashboardQuery(data).locations(null, null).isEmpty()) {
                throw new IllegalStateException("Bundled demo could not be analyzed");
            }
        }
    }
}
