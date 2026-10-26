package io.github.adrianpinson.worldtemps.exporter;

import io.github.adrianpinson.worldtemps.model.Reading;
import io.github.adrianpinson.worldtemps.service.TemperatureAnalysis.Series;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

/** UTF-8, locale-independent exports, replaced only after the complete CSV has been written. */
public final class CsvExporter {
    private CsvExporter() { }

    public static void writeReadings(Path path, List<Reading> readings) throws IOException {
        write(path, List.of("Region", "Country", "State", "City", "Month", "Day", "Year", "AvgTemperature"), printer -> {
            for (Reading reading : readings) {
                printer.printRecord(reading.region(), reading.country(), reading.state(), reading.city(),
                        reading.date().getMonthValue(), reading.date().getDayOfMonth(), reading.date().getYear(),
                        reading.averageTemperatureF());
            }
        });
    }

    public static void writeChart(Path path, List<Series> series) throws IOException {
        write(path, List.of("Country", "State", "City", "Resolution", "PeriodStart", "PeriodEnd",
                "AverageTemperatureF", "MinimumTemperatureF", "MaximumTemperatureF", "ReadingCount"), printer -> {
            for (Series city : series) {
                for (var point : city.points()) {
                    printer.printRecord(city.location().country(), city.location().state(), city.location().city(),
                            city.resolution().name(), point.start(), point.end(), point.average(),
                            point.minimum(), point.maximum(), point.count());
                }
            }
        });
    }

    private static void write(Path path, List<String> headers, WriteRows rows) throws IOException {
        Path target = path.toAbsolutePath();
        Path temporary = Files.createTempFile(target.getParent(), ".worldtemps-", ".csv");
        try {
            try (CSVPrinter printer = new CSVPrinter(Files.newBufferedWriter(temporary, StandardCharsets.UTF_8), CSVFormat.RFC4180)) {
                printer.printRecord(headers);
                rows.write(printer);
            }
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    @FunctionalInterface private interface WriteRows {
        void write(CSVPrinter printer) throws IOException;
    }
}
