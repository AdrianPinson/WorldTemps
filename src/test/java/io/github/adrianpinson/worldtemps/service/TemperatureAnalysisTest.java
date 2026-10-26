package io.github.adrianpinson.worldtemps.service;

import io.github.adrianpinson.worldtemps.model.Reading;
import io.github.adrianpinson.worldtemps.service.DashboardQuery.Filter;
import io.github.adrianpinson.worldtemps.service.DashboardQuery.Location;
import io.github.adrianpinson.worldtemps.service.TemperatureAnalysis.Resolution;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TemperatureAnalysisTest {
    private Reading reading(String country, String date, double temperature) {
        return new Reading("Europe", country, "", "London", LocalDate.parse(date), temperature);
    }

    @Test void rawModePreservesDuplicateReadingsAndSortsDates() {
        Reading first = reading("UK", "2025-01-01", 10);
        var series = TemperatureAnalysis.analyze(List.of(reading("UK", "2025-01-02", 20), first, first), Resolution.RAW);
        assertEquals(3, series.get(0).points().size());
        assertEquals(first.date(), series.get(0).points().get(0).start());
        assertEquals(1, series.get(0).points().get(0).count());
        assertThrows(UnsupportedOperationException.class, () -> series.get(0).points().clear());
    }

    @Test void dailyAveragesIncludeEachSourceRecord() {
        var point = TemperatureAnalysis.analyze(List.of(reading("UK", "2025-01-01", 10),
                reading("UK", "2025-01-01", 20), reading("UK", "2025-01-01", 30)), Resolution.DAILY).get(0).points().get(0);
        assertEquals(20, point.average());
        assertEquals(10, point.minimum());
        assertEquals(30, point.maximum());
        assertEquals(3, point.count());
    }

    @Test void monthlyBucketsRespectYearBoundariesAndLeapDays() {
        var points = TemperatureAnalysis.analyze(List.of(reading("UK", "2024-12-31", 10),
                reading("UK", "2025-01-01", 20), reading("UK", "2024-02-29", 30)), Resolution.MONTHLY).get(0).points();
        assertEquals(3, points.size());
        assertEquals(LocalDate.of(2024, 2, 1), points.get(0).start());
        assertEquals(LocalDate.of(2024, 2, 29), points.get(0).end());
        assertEquals(LocalDate.of(2025, 1, 1), points.get(2).start());
    }

    @Test void yearlyMeansAreWeightedBySourceReadingsNotMonthlyMeans() {
        var points = TemperatureAnalysis.analyze(List.of(reading("UK", "2025-01-01", 10),
                reading("UK", "2025-01-02", 10), reading("UK", "2025-02-01", 40),
                reading("UK", "2026-01-01", -5)), Resolution.YEARLY).get(0).points();
        assertEquals(2, points.size());
        assertEquals(20, points.get(0).average());
        assertEquals(3, points.get(0).count());
        assertEquals(LocalDate.of(2025, 12, 31), points.get(0).end());
    }

    @Test void sameNameLocationsStaySeparateAndMissingPeriodsStayMissing() {
        var series = TemperatureAnalysis.analyze(List.of(reading("UK", "2025-01-01", 10),
                reading("UK", "2025-03-01", 30), reading("Canada", "2025-01-01", -10)), Resolution.MONTHLY);
        assertEquals(2, series.size());
        var uk = series.stream().filter(s -> s.location().country().equals("UK")).findFirst().orElseThrow();
        assertEquals(2, uk.points().size());
        assertEquals(30, uk.points().get(1).average());
        assertTrue(TemperatureAnalysis.analyze(List.of(), Resolution.MONTHLY).isEmpty());
    }

    @Test void partialPeriodsUseOnlyReadingsInsideTheAppliedDateRange() {
        Reading included = reading("UK", "2025-01-20", 20);
        var query = new DashboardQuery(new TemperatureData(List.of(reading("UK", "2025-01-01", 100), included)));
        var result = query.apply(new Filter(null, null, Location.of(included), LocalDate.of(2025, 1, 15), LocalDate.of(2025, 1, 31)));
        var point = TemperatureAnalysis.analyze(result.readings(), Resolution.MONTHLY).get(0).points().get(0);
        assertEquals(20, point.average());
        assertEquals(1, point.count());
    }
}
