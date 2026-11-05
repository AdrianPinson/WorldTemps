package io.github.adrianpinson.worldtemps.service;

import io.github.adrianpinson.worldtemps.model.Reading;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

/** Monthly anomalies of daily means. Each observed baseline year receives equal weight. */
public final class TemperatureAnomalies {
    private TemperatureAnomalies() { }
    public record Month(int month, Double baselineF, Double targetF, Double anomalyF,
                        int baselineDays, int baselineExpectedDays, int baselineYearsObserved,
                        int targetDays, int targetExpectedDays) {
        public double baselineCoverage() { return baselineDays * 100.0 / baselineExpectedDays; }
        public double targetCoverage() { return targetDays * 100.0 / targetExpectedDays; }
    }
    public record Report(DashboardQuery.Location location, int baselineStart, int baselineEnd,
                         int targetYear, List<Month> months) {
        public Report { months = List.copyOf(months); }
    }
    public static Report analyze(List<Reading> readings, DashboardQuery.Location location,
                                 int baselineStart, int baselineEnd, int targetYear) {
        Objects.requireNonNull(location);
        if (baselineStart < 1 || baselineEnd > 9999 || baselineStart > baselineEnd || baselineEnd - baselineStart > 199
                || targetYear < 1 || targetYear > 9999 || targetYear >= baselineStart && targetYear <= baselineEnd)
            throw new IllegalArgumentException("Use a baseline of 1–200 years and a target year outside it (years 1–9999).");
        // Duplicate CSV records count as a single observed date; their daily value is their mean.
        Map<LocalDate, Double> days = readings.stream().filter(location::matches)
                .filter(r -> r.date().getYear() == targetYear || r.date().getYear() >= baselineStart && r.date().getYear() <= baselineEnd)
                .collect(Collectors.groupingBy(Reading::date, TreeMap::new, Collectors.averagingDouble(Reading::averageTemperatureF)));
        Map<YearMonth, DoubleSummaryStatistics> months = new HashMap<>();
        days.forEach((date, value) -> months.computeIfAbsent(YearMonth.from(date), key -> new DoubleSummaryStatistics()).accept(value));
        List<Month> result = new ArrayList<>();
        for (int month = 1; month <= 12; month++) {
            DoubleSummaryStatistics baseline = new DoubleSummaryStatistics();
            int observed = 0, expected = 0;
            for (int year = baselineStart; year <= baselineEnd; year++) {
                YearMonth period = YearMonth.of(year, month);
                expected += period.lengthOfMonth();
                var stats = months.get(period);
                if (stats != null) { observed += (int) stats.getCount(); baseline.accept(stats.getAverage()); }
            }
            var target = months.get(YearMonth.of(targetYear, month));
            Double normal = baseline.getCount() == 0 ? null : baseline.getAverage();
            Double value = target == null ? null : target.getAverage();
            result.add(new Month(month, normal, value, normal == null || value == null ? null : value - normal,
                    observed, expected, (int) baseline.getCount(), target == null ? 0 : (int) target.getCount(),
                    YearMonth.of(targetYear, month).lengthOfMonth()));
        }
        return new Report(location, baselineStart, baselineEnd, targetYear, result);
    }
}
