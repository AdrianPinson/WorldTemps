package io.github.adrianpinson.worldtemps.service;

import io.github.adrianpinson.worldtemps.model.Reading;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TemperatureDataTest {
    private Reading reading(String region, String country, String state, String city, int day, double temperature) {
        return new Reading(region, country, state, city, LocalDate.of(2025, 1, day), temperature);
    }

    private final Reading londonEarly = reading("Europe", "UK", "", "London", 1, 35);
    private final Reading londonLate = reading("Europe", "UK", "", "London", 2, 40);
    private final Reading londonCanada = reading("North America", "Canada", "Ontario", "London", 1, 10);
    private final Reading londonUs = reading("North America", "US", "Ohio", "London", 1, 90);
    private final TemperatureData data = new TemperatureData(List.of(londonLate, londonCanada, londonEarly, londonUs));

    @Test void findsGlobalExtrema() {
        assertEquals(londonCanada, data.minimum().orElseThrow());
        assertEquals(londonUs, data.maximum().orElseThrow());
        assertEquals(4, data.size());
    }

    @Test void filtersCountryAndRegionIgnoringCaseAndOuterWhitespace() {
        assertEquals(londonEarly, data.minimumByCountry(" uk ").orElseThrow());
        assertEquals(londonLate, data.maximumByRegion(" EUROPE ").orElseThrow());
    }

    @Test void distinguishesCitiesWithTheSameName() {
        assertEquals(londonLate, data.maximumByCity("UK", "", "London").orElseThrow());
        assertEquals(londonCanada, data.maximumByCity("Canada", "Ontario", "London").orElseThrow());
        assertTrue(data.maximumByCity("US", "Texas", "London").isEmpty());
    }

    @Test void cityHistoryIsSortedAndCannotMutateTheDataset() {
        List<Reading> history = data.cityHistory(" uk ", "", " london ");
        assertEquals(List.of(londonEarly, londonLate), history);
        assertThrows(UnsupportedOperationException.class, () -> history.clear());
        assertEquals(4, data.size());
    }

    @Test void handlesEmptyDataAndMissingMatches() {
        TemperatureData empty = new TemperatureData(List.of());
        assertTrue(empty.minimum().isEmpty());
        assertTrue(empty.maximum().isEmpty());
        assertTrue(data.minimumByCountry("Atlantis").isEmpty());
        assertTrue(data.maximumByRegion(null).isEmpty());
        assertTrue(data.cityHistory("UK", "", "Missing").isEmpty());
    }

    @Test void snapshotsTheInputCollection() {
        List<Reading> source = new ArrayList<>(List.of(londonEarly));
        TemperatureData snapshot = new TemperatureData(source);
        source.clear();
        assertEquals(1, snapshot.size());
    }
}
