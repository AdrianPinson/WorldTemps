package io.github.adrianpinson.worldtemps.service;

import io.github.adrianpinson.worldtemps.model.Reading;
import io.github.adrianpinson.worldtemps.service.DashboardQuery.Location;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.DoubleSummaryStatistics;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;

/** Aggregates each location independently; missing periods are never filled with zero. */
public final class TemperatureAnalysis {
    private TemperatureAnalysis() { }

    public static List<Series> analyze(List<Reading> readings, Resolution resolution) {
        Objects.requireNonNull(resolution);
        Map<Location, List<Reading>> locations = readings.stream().collect(Collectors.groupingBy(Location::of,
                () -> new TreeMap<>(Comparator.comparing(Location::toString)), Collectors.toList()));
        List<Series> series = new ArrayList<>();
        locations.forEach((location, values) -> {
            List<Point> points;
            if (resolution == Resolution.RAW) {
                points = values.stream().sorted(Comparator.comparing(Reading::date))
                        .map(r -> new Point(r.date(), r.date(), r.averageTemperatureF(),
                                r.averageTemperatureF(), r.averageTemperatureF(), 1)).toList();
            } else {
                Map<LocalDate, List<Reading>> groups = values.stream().collect(Collectors.groupingBy(
                        r -> resolution.periodStart(r.date()), TreeMap::new, Collectors.toList()));
                points = groups.entrySet().stream().map(entry -> {
                    DoubleSummaryStatistics stats = entry.getValue().stream()
                            .mapToDouble(Reading::averageTemperatureF).summaryStatistics();
                    return new Point(entry.getKey(), resolution.periodEnd(entry.getKey()), stats.getAverage(),
                            stats.getMin(), stats.getMax(), stats.getCount());
                }).toList();
            }
            series.add(new Series(location, resolution, points));
        });
        return List.copyOf(series);
    }

    public enum Resolution {
        RAW("Raw readings"), DAILY("Daily averages"), MONTHLY("Monthly averages"), YEARLY("Yearly averages");
        private final String label;
        Resolution(String label) { this.label = label; }
        @Override public String toString() { return label; }

        public LocalDate periodStart(LocalDate date) {
            return switch (this) {
                case RAW, DAILY -> date;
                case MONTHLY -> date.withDayOfMonth(1);
                case YEARLY -> date.withDayOfYear(1);
            };
        }

        public LocalDate periodEnd(LocalDate start) {
            return switch (this) {
                case RAW, DAILY -> start;
                case MONTHLY -> YearMonth.from(start).atEndOfMonth();
                case YEARLY -> LocalDate.of(start.getYear(), 12, 31);
            };
        }
    }

    public record Point(LocalDate start, LocalDate end, double average, double minimum, double maximum, long count) { }
    public record Series(Location location, Resolution resolution, List<Point> points) {
        public Series { points = List.copyOf(points); }
    }
}
