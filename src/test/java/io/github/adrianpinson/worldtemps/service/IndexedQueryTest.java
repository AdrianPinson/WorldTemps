package io.github.adrianpinson.worldtemps.service;

import io.github.adrianpinson.worldtemps.model.Reading;
import io.github.adrianpinson.worldtemps.service.DashboardQuery.*;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class IndexedQueryTest {
    @Test void indexedResultsExactlyMatchIndependentScanAcrossRandomizedFilters() {
        Random random = new Random(20261026);
        LocalDate first = LocalDate.of(2024, 1, 1);
        List<Reading> rows = new ArrayList<>();
        for (int day = 0; day < 180; day++) {
            for (int city = 0; city < 20; city++) {
                rows.add(new Reading(city % 2 == 0 ? "Europe" : "America", city % 2 == 0 ? "UK" : "US",
                        city % 2 == 0 ? "" : "State" + city, "City" + city, first.plusDays(day), random.nextDouble() * 100 - 20));
            }
        }
        rows.addAll(rows.subList(0, 40));
        Collections.shuffle(rows, random);
        DashboardQuery query = new DashboardQuery(new TemperatureData(rows));
        List<Location> cities = query.locations(null, null);
        for (int sample = 0; sample < 400; sample++) {
            Location primary = sample % 4 == 0 ? null : cities.get(random.nextInt(cities.size()));
            List<Location> comparisons = primary == null ? List.of() : List.of(cities.get(random.nextInt(cities.size())), cities.get(random.nextInt(cities.size())));
            int left = random.nextInt(220) - 20;
            int right = left + random.nextInt(180);
            Filter filter = new Filter(sample % 3 == 0 ? " europe " : null, sample % 5 == 0 ? "us" : null, primary,
                    sample % 7 == 0 ? null : first.plusDays(left), sample % 9 == 0 ? null : first.plusDays(right));
            assertEquals(ReferenceScan.apply(rows, filter, comparisons), query.apply(filter, comparisons), "Filter " + sample);
        }
    }

    @Test void caseInsensitiveAliasesDoNotDuplicateIndexedBuckets() {
        LocalDate day = LocalDate.of(2025, 1, 1);
        var rows = List.of(new Reading("America", "US", "Texas", "I", day, 10),
                new Reading("America", "us", "texas", "i", day, 20));
        DashboardQuery query = new DashboardQuery(new TemperatureData(rows));
        Filter filter = new Filter(null, null, new Location("us", "texas", "ı"), day, day);
        var comparisons = List.of(new Location("US", "Texas", "I"));
        assertEquals(ReferenceScan.apply(rows, filter, comparisons), query.apply(filter, comparisons));
        assertEquals(2, query.apply(filter, comparisons).readings().size());
    }

    @Test void geographyStillAppliesWhenACityBucketContainsDifferentRegions() {
        LocalDate day = LocalDate.of(2025, 1, 1);
        var rows = List.of(new Reading("One", "US", "Texas", "Longview", day, 10),
                new Reading("Two", "US", "Texas", "Longview", day, 20));
        var query = new DashboardQuery(new TemperatureData(rows));
        var result = query.apply(new Filter("Two", null, new Location("US", "Texas", "Longview"), null, null));
        assertEquals(1, result.readings().size());
        assertEquals(20, result.summary().orElseThrow().average());
    }

    @Test void dateBoundsHandleRangesEntirelyOutsideTheDatasetAndRetainAllBoundaryDuplicates() {
        LocalDate day = LocalDate.of(2025, 1, 1);
        Reading row = new Reading("Europe", "UK", "", "London", day, 10);
        var query = new DashboardQuery(new TemperatureData(List.of(row, row, row)));
        var city = Location.of(row);
        assertEquals(3, query.apply(new Filter(null, null, city, day, day)).readings().size());
        assertTrue(query.apply(new Filter(null, null, city, day.minusDays(5), day.minusDays(1))).readings().isEmpty());
        assertTrue(query.apply(new Filter(null, null, null, day.plusDays(1), day.plusDays(5))).readings().isEmpty());
    }
}
