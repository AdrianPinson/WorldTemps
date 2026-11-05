package io.github.adrianpinson.worldtemps.service;

import io.github.adrianpinson.worldtemps.model.Reading;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TemperatureAnomaliesTest {
    final DashboardQuery.Location city = new DashboardQuery.Location("US", "Texas", "Longview");
    Reading r(int year, int month, int day, double value) {
        return new Reading("America", "US", "Texas", "Longview", LocalDate.of(year, month, day), value);
    }
    @Test void equallyWeightsYearsAndDeduplicatesDatesBeforeCalculatingCoverage() {
        var rows = List.of(r(2020,1,1,10), r(2020,1,1,30), r(2020,1,2,20), r(2021,1,1,40), r(2022,1,1,35));
        var month = TemperatureAnomalies.analyze(rows, city, 2020, 2021, 2022).months().get(0);
        assertEquals(30.0, month.baselineF()); assertEquals(5.0, month.anomalyF());
        assertEquals(3, month.baselineDays()); assertEquals(62, month.baselineExpectedDays());
        assertEquals(2, month.baselineYearsObserved()); assertEquals(1, month.targetDays());
    }
    @Test void reportsMissingDataWithoutZeroFillAndCountsLeapDays() {
        var report = TemperatureAnomalies.analyze(List.of(r(2020,2,29,20), r(2022,3,1,40)), city, 2020, 2021, 2022);
        assertEquals(12, report.months().size());
        assertEquals(57, report.months().get(1).baselineExpectedDays());
        assertNull(report.months().get(1).targetF()); assertNull(report.months().get(1).anomalyF());
        assertNull(report.months().get(2).baselineF()); assertNull(report.months().get(2).anomalyF());
        assertEquals(0.0, report.months().get(0).targetCoverage());
    }
    @Test void rejectsOverlapAndInvalidYearRanges() {
        assertThrows(IllegalArgumentException.class, () -> TemperatureAnomalies.analyze(List.of(), city, 2020, 2022, 2021));
        assertThrows(IllegalArgumentException.class, () -> TemperatureAnomalies.analyze(List.of(), city, 2022, 2020, 2023));
        assertThrows(IllegalArgumentException.class, () -> TemperatureAnomalies.analyze(List.of(), city, 1, 9999, 10000));
    }
    @Test void isolatesTheChosenLocationAndPreservesNegativeAnomalies() {
        var other = new Reading("Europe", "UK", "", "London", LocalDate.of(2020,1,1), 999);
        var month = TemperatureAnomalies.analyze(List.of(r(2020,1,1,50), other, r(2021,1,1,40)), city, 2020, 2020, 2021).months().get(0);
        assertEquals(-10.0, month.anomalyF());
    }
}
