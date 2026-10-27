package io.github.adrianpinson.worldtemps.service;

import io.github.adrianpinson.worldtemps.model.Reading;
import java.time.LocalDate;
import java.util.*;

/** Immutable chronological and location indexes shared by dashboard queries. */
public final class DashboardQuery {
    private static final Comparator<Reading> ORDER = Comparator.comparing(Reading::date)
            .thenComparing(Reading::city).thenComparing(Reading::country).thenComparing(Reading::state);
    private static final Comparator<Location> LOCATION_ORDER = Comparator.comparing(Location::country, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(Location::state, String.CASE_INSENSITIVE_ORDER).thenComparing(Location::city, String.CASE_INSENSITIVE_ORDER);
    private final List<Reading> readings;
    private final Map<Location, List<Reading>> byLocation;
    private final List<GeoOption> options;
    private final List<String> regions;

    public DashboardQuery(TemperatureData data) {
        List<Reading> source = Objects.requireNonNull(data).readings();
        options = source.stream().map(r -> new GeoOption(r.region(), Location.of(r))).distinct().toList();
        regions = options.stream().map(GeoOption::region).distinct().sorted().toList();
        readings = source.stream().sorted(ORDER).toList();
        Map<Location, List<Reading>> index = new TreeMap<>(LOCATION_ORDER);
        for (Reading reading : readings) {
            index.computeIfAbsent(Location.of(reading), key -> new ArrayList<>()).add(reading);
        }
        index.replaceAll((location, values) -> List.copyOf(values));
        byLocation = Collections.unmodifiableMap(index);
    }

    public List<String> regions() { return regions; }

    public List<String> countries(String region) {
        return options.stream().filter(option -> matches(option.region(), region))
                .map(option -> option.location().country()).distinct().sorted().toList();
    }

    public List<Location> locations(String region, String country) {
        return options.stream().filter(option -> matches(option.region(), region) && matches(option.location().country(), country))
                .map(GeoOption::location).distinct().sorted(Comparator.comparing(Location::toString)).toList();
    }

    public Optional<LocalDate> firstDate() {
        return readings.isEmpty() ? Optional.empty() : Optional.of(readings.get(0).date());
    }

    public Optional<LocalDate> lastDate() {
        return readings.isEmpty() ? Optional.empty() : Optional.of(readings.get(readings.size() - 1).date());
    }

    public Result apply(Filter filter) { return apply(filter, List.of()); }

    /** Geography and inclusive date bounds apply to all selected cities. */
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
        List<Reading> selected = new ArrayList<>();
        if (locations.isEmpty()) {
            for (Reading reading : range(readings, filter.from(), filter.to())) {
                if (geographyMatches(reading, filter)) { selected.add(reading); }
            }
        } else {
            // At most three already-sorted ranges: merge without sorting the result again.
            PriorityQueue<Cursor> queue = new PriorityQueue<>(Comparator.comparing(Cursor::current, ORDER));
            Set<List<Reading>> visited = Collections.newSetFromMap(new IdentityHashMap<>());
            for (Location location : locations) {
                List<Reading> bucket = byLocation.get(location);
                if (bucket != null && visited.add(bucket)) {
                    List<Reading> slice = range(bucket, filter.from(), filter.to());
                    if (!slice.isEmpty()) { queue.add(new Cursor(slice)); }
                }
            }
            while (!queue.isEmpty()) {
                Cursor cursor = queue.remove();
                if (geographyMatches(cursor.current(), filter)) { selected.add(cursor.current()); }
                if (cursor.advance()) { queue.add(cursor); }
            }
        }
        if (selected.isEmpty()) { return new Result(selected, Optional.empty()); }
        DoubleSummaryStatistics stats = selected.stream().mapToDouble(Reading::averageTemperatureF).summaryStatistics();
        return new Result(selected, Optional.of(new Summary(stats.getMin(), stats.getMax(), stats.getAverage(),
                selected.size(), selected.stream().map(Location::of).distinct().count())));
    }

    private static boolean geographyMatches(Reading reading, Filter filter) {
        return matches(reading.region(), filter.region()) && matches(reading.country(), filter.country());
    }

    private static List<Reading> range(List<Reading> rows, LocalDate from, LocalDate to) {
        int start = from == null ? 0 : bound(rows, from, false);
        int end = to == null ? rows.size() : bound(rows, to, true);
        return rows.subList(start, end);
    }

    /** lower_bound for the first day; upper_bound for the inclusive final day. */
    private static int bound(List<Reading> rows, LocalDate date, boolean upper) {
        int low = 0;
        int high = rows.size();
        while (low < high) {
            int middle = (low + high) >>> 1;
            int comparison = rows.get(middle).date().compareTo(date);
            if (comparison < 0 || (upper && comparison == 0)) { low = middle + 1; }
            else { high = middle; }
        }
        return low;
    }

    private record GeoOption(String region, Location location) { }
    private static final class Cursor {
        private final List<Reading> rows;
        private int index;
        private Cursor(List<Reading> rows) { this.rows = rows; }
        private Reading current() { return rows.get(index); }
        private boolean advance() { return ++index < rows.size(); }
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
