package io.github.adrianpinson.worldtemps.service;

import io.github.adrianpinson.worldtemps.model.Reading;
import io.github.adrianpinson.worldtemps.service.DashboardQuery.*;
import java.util.*;

/** Independent pre-index scan/sort algorithm, retained only for equivalence tests and measurements. */
final class ReferenceScan {
    private ReferenceScan() { }
    static Result apply(List<Reading> source, Filter filter, List<Location> comparisons) {
        List<Location> locations = java.util.stream.Stream.concat(java.util.stream.Stream.ofNullable(filter.location()), comparisons.stream()).distinct().toList();
        List<Reading> selected = source.stream()
                .filter(r -> filter.region() == null || r.region().equalsIgnoreCase(filter.region().trim()))
                .filter(r -> filter.country() == null || r.country().equalsIgnoreCase(filter.country().trim()))
                .filter(r -> locations.isEmpty() || locations.stream().anyMatch(location -> location.matches(r)))
                .filter(r -> filter.from() == null || !r.date().isBefore(filter.from()))
                .filter(r -> filter.to() == null || !r.date().isAfter(filter.to()))
                .sorted(Comparator.comparing(Reading::date).thenComparing(Reading::city).thenComparing(Reading::country).thenComparing(Reading::state)).toList();
        if (selected.isEmpty()) { return new Result(selected, Optional.empty()); }
        DoubleSummaryStatistics stats = selected.stream().mapToDouble(Reading::averageTemperatureF).summaryStatistics();
        return new Result(selected, Optional.of(new Summary(stats.getMin(), stats.getMax(), stats.getAverage(), selected.size(),
                selected.stream().map(Location::of).distinct().count())));
    }
}
