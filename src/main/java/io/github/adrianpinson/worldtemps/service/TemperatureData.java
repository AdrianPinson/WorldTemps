package io.github.adrianpinson.worldtemps.service;

import io.github.adrianpinson.worldtemps.collection.ReadingList;
import io.github.adrianpinson.worldtemps.model.Reading;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/** UI-independent queries over a snapshot of the imported readings. */
public final class TemperatureData {
    private final ReadingList readings = new ReadingList();
    private static final Comparator<Reading> BY_TEMPERATURE =
            Comparator.comparingDouble(Reading::averageTemperatureF);

    public TemperatureData(Iterable<Reading> source) {
        Objects.requireNonNull(source, "Readings are required").forEach(readings::add);
    }

    public int size() {
        return readings.size();
    }

    public Optional<Reading> minimum() {
        return stream().min(BY_TEMPERATURE);
    }

    public Optional<Reading> maximum() {
        return stream().max(BY_TEMPERATURE);
    }

    public Optional<Reading> minimumByCountry(String country) {
        return stream().filter(r -> matches(r.country(), country)).min(BY_TEMPERATURE);
    }

    public Optional<Reading> maximumByRegion(String region) {
        return stream().filter(r -> matches(r.region(), region)).max(BY_TEMPERATURE);
    }

    public Optional<Reading> maximumByCity(String country, String state, String city) {
        return stream().filter(location(country, state, city)).max(BY_TEMPERATURE);
    }

    public List<Reading> cityHistory(String country, String state, String city) {
        return stream().filter(location(country, state, city))
                .sorted(Comparator.comparing(Reading::date)).toList();
    }

    private Stream<Reading> stream() {
        return StreamSupport.stream(readings.spliterator(), false);
    }

    private static Predicate<Reading> location(String country, String state, String city) {
        return r -> matches(r.country(), country) && matches(r.state(), state) && matches(r.city(), city);
    }

    private static boolean matches(String value, String query) {
        return query != null && value.equalsIgnoreCase(query.trim());
    }
}
