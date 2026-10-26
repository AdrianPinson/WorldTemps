package io.github.adrianpinson.worldtemps.exporter;

import io.github.adrianpinson.worldtemps.importer.CsvTemperatureLoader;
import io.github.adrianpinson.worldtemps.model.Reading;
import io.github.adrianpinson.worldtemps.service.TemperatureAnalysis;
import io.github.adrianpinson.worldtemps.service.TemperatureAnalysis.Resolution;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import org.apache.commons.csv.CSVFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class CsvExporterTest {
    @TempDir Path directory;
    private Reading reading(String city, String date, double temperature) {
        return new Reading("South America", "Brazil", "", city, LocalDate.parse(date), temperature);
    }

    @Test void rawExportRoundTripsQuotedUnicodeLocationsAndFullPrecision() throws Exception {
        List<Reading> original = List.of(reading("São \"João\", Sul", "2025-01-01", 42.123456789),
                reading("São \"João\", Sul", "2025-01-02", -0.0));
        Path file = directory.resolve("readings.csv");
        CsvExporter.writeReadings(file, original);
        var imported = new CsvTemperatureLoader().load(Files.newBufferedReader(file));
        assertEquals(original, imported.data().readings());
        assertEquals(0, imported.rejectedRows());
    }

    @Test void chartExportIncludesPeriodStatisticsAndSourceCounts() throws Exception {
        var series = TemperatureAnalysis.analyze(List.of(reading("City", "2024-02-01", 10),
                reading("City", "2024-02-29", 20)), Resolution.MONTHLY);
        Path file = directory.resolve("chart.csv");
        CsvExporter.writeChart(file, series);
        try (var parser = CSVFormat.RFC4180.builder().setHeader().setSkipHeaderRecord(true).build().parse(Files.newBufferedReader(file))) {
            var rows = parser.getRecords();
            assertEquals(1, rows.size());
            assertEquals("2024-02-01", rows.get(0).get("PeriodStart"));
            assertEquals("2024-02-29", rows.get(0).get("PeriodEnd"));
            assertEquals("15.0", rows.get(0).get("AverageTemperatureF"));
            assertEquals("10.0", rows.get(0).get("MinimumTemperatureF"));
            assertEquals("20.0", rows.get(0).get("MaximumTemperatureF"));
            assertEquals("2", rows.get(0).get("ReadingCount"));
            assertEquals("MONTHLY", rows.get(0).get("Resolution"));
        }
    }

    @Test void decimalsAreLocaleIndependentAndExistingExportsAreReplaced() throws Exception {
        Locale previous = Locale.getDefault();
        Path file = directory.resolve("readings.csv");
        Files.writeString(file, "old data");
        try {
            Locale.setDefault(Locale.GERMANY);
            CsvExporter.writeReadings(file, List.of(reading("City", "2025-01-01", 42.125)));
        } finally {
            Locale.setDefault(previous);
        }
        assertTrue(Files.readString(file).contains("42.125"));
        assertFalse(Files.readString(file).contains("old data"));
        try (var paths = Files.list(directory)) { assertEquals(1, paths.count()); }
    }

    @Test void failedDestinationPreservesExistingContentAndCleansTemporaryFile() throws Exception {
        Path destination = directory.resolve("existing-directory");
        Files.createDirectory(destination);
        Files.writeString(destination.resolve("keep.txt"), "keep me");
        assertThrows(java.io.IOException.class, () -> CsvExporter.writeReadings(destination,
                List.of(reading("City", "2025-01-01", 40))));
        assertEquals("keep me", Files.readString(destination.resolve("keep.txt")));
        try (var paths = Files.list(directory)) { assertEquals(List.of(destination), paths.toList()); }
    }

    @Test void rawExportsKeepTheSuppliedSortOrder() throws Exception {
        var first = reading("City", "2025-01-02", 20);
        var second = reading("City", "2025-01-01", 10);
        Path file = directory.resolve("readings.csv");
        CsvExporter.writeReadings(file, List.of(first, second));
        assertEquals(List.of(first, second), new CsvTemperatureLoader().load(Files.newBufferedReader(file)).data().readings());
    }
}
