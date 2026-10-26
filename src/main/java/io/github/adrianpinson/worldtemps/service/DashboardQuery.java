package io.github.adrianpinson.worldtemps.service;

import io.github.adrianpinson.worldtemps.model.Reading;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.DoubleSummaryStatistics;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Shared filtering and summaries for the dashboard, without a JavaFX dependency. */
public final class DashboardQuery {
    private final List<Reading> readings;

    public DashboardQuery(TemperatureData data) {
        readings = Objects.requireNonNull(data).readings();
    }

    public List<String> regions() {
        return readings.stream().map(Reading::region).distinct().sorted().toList();
    }

    public List<String> countries(String region) {
        return readings.stream().filter(r -> matches(r.region(), region))
                .map(Reading::country).distinct().sorted().toList();
    }

    public List<Location> locations(String region, String country) {
        return readings.stream().filter(r -> matches(r.region(), region) && matches(r.country(), country))
                .map(Location::of).distinct().sorted(Comparator.comparing(Location::toString)).toList();
    }

    public Optional<LocalDate> firstDate() {
        return readings.stream().map(Reading::date).min(Comparator.naturalOrder());
    }

    public Optional<LocalDate> lastDate() {
        return readings.stream().map(Reading::date).max(Comparator.naturalOrder());
    }

    public Result apply(Filter filter) {
        return apply(filter, List.of());
    }

    /** Comparison cities share the primary selection's geography and date bounds. */
    public Result apply(Filter filter, List<Location> comparisons) {
        Objects.requireNonNull(filter);
        Objects.requireNonNull(comparisons);
        if (filter.location() == null && !comparisons.isEmpty()) {
            throw new IllegalArgumentException("Select a primary city before adding comparisons.");
        }
        List<Location> locations = java.util.stream.Stream.concat(
                java.util.stream.Stream.ofNullable(filter.location()), comparisons.stream()).distinct().toList();
        if (locations.size() > 3 || locations.contains(null)) {
            throw new IllegalArgumentException("Choose up to three valid cities.");
        }
        List<Reading> selected = readings.stream()
                .filter(r -> matches(r.region(), filter.region()) && matches(r.country(), filter.country()))
                .filter(r -> locations.isEmpty() || locations.stream().anyMatch(location -> location.matches(r)))
                .filter(r -> filter.from() == null || !r.date().isBefore(filter.from()))
                .filter(r -> filter.to() == null || !r.date().isAfter(filter.to()))
                .sorted(Comparator.comparing(Reading::date).thenComparing(Reading::city)
                        .thenComparing(Reading::country).thenComparing(Reading::state))
                .toList();
        if (selected.isEmpty()) {
            return new Result(selected, Optional.empty());
        }
        DoubleSummaryStatistics stats = selected.stream().mapToDouble(Reading::averageTemperatureF).summaryStatistics();
        return new Result(selected, Optional.of(new Summary(stats.getMin(), stats.getMax(), stats.getAverage(),
                selected.size(), selected.stream().map(Location::of).distinct().count())));
    }

    private static boolean matches(String value, String filter) {
        return filter == null || value.equalsIgnoreCase(filter.trim());
    }

    public record Location(String country, String state, String city) {
        public Location {
            country = Objects.requireNonNull(country).trim();
            state = Objects.requireNonNullElse(state, "").trim();
            city = Objects.requireNonNull(city).trim();
        }

        public static Location of(Reading reading) {
            return new Location(reading.country(), reading.state(), reading.city());
        }

        public boolean matches(Reading reading) {
            return country.equalsIgnoreCase(reading.country()) && state.equalsIgnoreCase(reading.state())
                    && city.equalsIgnoreCase(reading.city());
        }

        @Override public String toString() {
            return city + (state.isEmpty() ? "" : ", " + state) + " · " + country;
        }
    }

    public record Filter(String region, String country, Location location, LocalDate from, LocalDate to) {
        public Filter {
            if (from != null && to != null && from.isAfter(to)) {
                throw new IllegalArgumentException("Start date must be on or before the end date.");
            }
        }
    }

    public record Summary(double minimum, double maximum, double average, int count, long cities) { }

    public record Result(List<Reading> readings, Optional<Summary> summary) {
        public Result {
            readings = List.copyOf(readings);
        }
    }
}
