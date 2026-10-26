package io.github.adrianpinson.worldtemps.service;

import io.github.adrianpinson.worldtemps.model.Reading;
import io.github.adrianpinson.worldtemps.service.DashboardQuery.Filter;
import io.github.adrianpinson.worldtemps.service.DashboardQuery.Location;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DashboardQueryTest {
    private final LocalDate day = LocalDate.of(2025, 1, 1);
    private final Reading london = new Reading("Europe", "UK", "", "London", day, 40);
    private final Reading londonLater = new Reading("Europe", "UK", "", "London", day.plusDays(1), 50);
    private final Reading canada = new Reading("North America", "Canada", "Ontario", "London", day, -10);
    private final DashboardQuery query = new DashboardQuery(new TemperatureData(List.of(londonLater, canada, london)));

    @Test void summaryUsesExactlyTheFilteredReadings() {
        var result = query.apply(new Filter(" Europe ", "uk", Location.of(london), null, null));
        assertEquals(List.of(london, londonLater), result.readings());
        var summary = result.summary().orElseThrow();
        assertEquals(40, summary.minimum());
        assertEquals(50, summary.maximum());
        assertEquals(45, summary.average());
        assertEquals(2, summary.count());
        assertEquals(1, summary.cities());
    }

    @Test void datesAreInclusiveAndBoundsMayBeOmitted() {
        assertEquals(List.of(londonLater), query.apply(new Filter(null, null, Location.of(london), day.plusDays(1), day.plusDays(1))).readings());
        assertEquals(2, query.apply(new Filter(null, null, null, null, day)).readings().size());
        assertEquals(1, query.apply(new Filter(null, null, null, day.plusDays(1), null)).readings().size());
    }

    @Test void geographyOptionsCascadeAndKeepSameNameCitiesSeparate() {
        assertEquals(List.of("Europe", "North America"), query.regions());
        assertEquals(List.of("UK"), query.countries("Europe"));
        assertEquals(2, query.locations(null, null).size());
        assertEquals(List.of(Location.of(canada)), query.locations(null, "Canada"));
        assertNotEquals(Location.of(london).toString(), Location.of(canada).toString());
    }

    @Test void emptyMatchesHaveNoFabricatedSummaryOrReadings() {
        var result = query.apply(new Filter(null, "Missing", null, null, null));
        assertTrue(result.readings().isEmpty());
        assertTrue(result.summary().isEmpty());
        DashboardQuery empty = new DashboardQuery(new TemperatureData(List.of()));
        assertTrue(empty.firstDate().isEmpty());
        assertTrue(empty.lastDate().isEmpty());
        assertTrue(empty.locations(null, null).isEmpty());
    }

    @Test void dateDefaultsCoverAllDataAndResultsAreImmutable() {
        assertEquals(day, query.firstDate().orElseThrow());
        assertEquals(day.plusDays(1), query.lastDate().orElseThrow());
        assertThrows(UnsupportedOperationException.class, () -> query.apply(new Filter(null, null, null, null, null)).readings().clear());
    }

    @Test void rejectsReversedDateRanges() {
        assertThrows(IllegalArgumentException.class, () -> new Filter(null, null, null, day.plusDays(1), day));
    }

    @Test void comparisonsUseOneDateRangeAndDeduplicateSelectedCities() {
        var result = query.apply(new Filter(null, null, Location.of(london), day, day),
                List.of(Location.of(canada), Location.of(london), Location.of(canada)));
        assertEquals(2, result.readings().size());
        assertEquals(2, result.summary().orElseThrow().cities());
        assertEquals(15, result.summary().orElseThrow().average());
    }

    @Test void comparisonsRespectTheRegionAndCountryFilters() {
        var result = query.apply(new Filter("Europe", "UK", Location.of(london), null, null), List.of(Location.of(canada)));
        assertEquals(2, result.readings().size());
        assertTrue(result.readings().stream().allMatch(r -> r.country().equals("UK")));
    }

    @Test void comparisonRequiresPrimaryCityAndAtMostThreeDistinctLocations() {
        assertThrows(IllegalArgumentException.class, () -> query.apply(new Filter(null, null, null, null, null), List.of(Location.of(canada))));
        assertThrows(IllegalArgumentException.class, () -> query.apply(new Filter(null, null, Location.of(london), null, null),
                List.of(Location.of(canada), new Location("US", "Texas", "Longview"), new Location("Japan", "", "Tokyo"))));
    }

    @Test void differentStatesDoNotMergeAndDuplicatesRemainSourceRecords() {
        Reading ohio = new Reading("North America", "US", "Ohio", "London", day, 80);
        Reading kentucky = new Reading("North America", "US", "Kentucky", "London", day, 70);
        var local = new DashboardQuery(new TemperatureData(List.of(ohio, ohio, kentucky)));
        assertEquals(2, local.locations(null, "US").size());
        assertEquals(2, local.apply(new Filter(null, null, Location.of(ohio), null, null)).readings().size());
        assertEquals(2, local.apply(new Filter(null, null, null, null, null)).summary().orElseThrow().cities());
    }
}
