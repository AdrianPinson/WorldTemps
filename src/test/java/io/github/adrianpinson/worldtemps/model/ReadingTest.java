package io.github.adrianpinson.worldtemps.model;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class ReadingTest {
    @Test void trimsLocationAndAllowsMissingState() {
        Reading reading = new Reading(" Europe ", " UK ", null, " London ", LocalDate.of(2025, 1, 1), -10);
        assertEquals("", reading.state());
        assertEquals("London", reading.city());
        assertEquals("2025-01-01 | London, UK | -10.0 °F", reading.toString());
    }

    @ParameterizedTest
    @ValueSource(doubles = {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void rejectsNonfiniteTemperature(double temperature) {
        assertThrows(IllegalArgumentException.class, () -> new Reading("Europe", "UK", "", "London",
                LocalDate.of(2025, 1, 1), temperature));
    }

    @Test void requiresLocationAndDate() {
        assertThrows(IllegalArgumentException.class, () -> new Reading("Europe", "UK", "", " ", LocalDate.now(), 40));
        assertThrows(NullPointerException.class, () -> new Reading("Europe", "UK", "", "London", null, 40));
    }
}
