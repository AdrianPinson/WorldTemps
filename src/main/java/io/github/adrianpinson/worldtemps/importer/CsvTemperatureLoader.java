package io.github.adrianpinson.worldtemps.importer;

import io.github.adrianpinson.worldtemps.model.Reading;
import io.github.adrianpinson.worldtemps.service.TemperatureData;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

/** Strict CSV syntax, with recoverable row-level data validation failures. */
public final class CsvTemperatureLoader {
    private static final List<String> REQUIRED_HEADERS =
            List.of("Region", "Country", "State", "City", "Month", "Day", "Year", "AvgTemperature");
    private static final int MAX_REPORTED_ISSUES = 20;
    private static final CSVFormat FORMAT = CSVFormat.RFC4180.builder()
            .setHeader().setSkipHeaderRecord(true).setIgnoreEmptyLines(true)
            .setIgnoreHeaderCase(true).setTrim(true).build();

    /** Consumes and closes the supplied reader. -99 is the source dataset's missing-value marker. */
    public ImportResult load(Reader reader) throws IOException {
        List<Reading> readings = new ArrayList<>();
        List<ImportIssue> issues = new ArrayList<>();
        int rejected = 0;
        try (CSVParser parser = FORMAT.parse(reader)) {
            for (String header : REQUIRED_HEADERS) {
                if (!parser.getHeaderMap().containsKey(header)) {
                    throw new IOException("Missing required CSV column: " + header);
                }
            }
            for (CSVRecord record : parser) {
                try {
                    if (!record.isConsistent()) {
                        throw new IllegalArgumentException("Column count does not match the header");
                    }
                    double temperature = Double.parseDouble(record.get("AvgTemperature"));
                    if (temperature == -99.0) {
                        throw new IllegalArgumentException("Missing temperature marker (-99)");
                    }
                    LocalDate date = LocalDate.of(Integer.parseInt(record.get("Year")),
                            Integer.parseInt(record.get("Month")), Integer.parseInt(record.get("Day")));
                    readings.add(new Reading(record.get("Region"), record.get("Country"),
                            record.get("State"), record.get("City"), date, temperature));
                } catch (IllegalArgumentException | DateTimeException e) {
                    rejected++;
                    if (issues.size() < MAX_REPORTED_ISSUES) {
                        issues.add(new ImportIssue(record.getRecordNumber() + 1, e.getMessage()));
                    }
                }
            }
        } catch (UncheckedIOException e) {
            throw new IOException("Invalid CSV syntax: " + e.getCause().getMessage(), e.getCause());
        }
        return new ImportResult(new TemperatureData(readings), rejected, issues);
    }

    /** Record number includes the header; a quoted record may span multiple physical lines. */
    public record ImportIssue(long recordNumber, String reason) { }

    public record ImportResult(TemperatureData data, int rejectedRows, List<ImportIssue> issues) {
        public ImportResult {
            issues = List.copyOf(issues);
        }
    }
}
