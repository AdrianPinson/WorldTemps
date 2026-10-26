package io.github.adrianpinson.worldtemps.model;

import java.time.LocalDate;
import java.util.Locale;
import java.util.Objects;

/** One validated daily reading. The source CSV reports temperatures in Fahrenheit. */
public record Reading(String region, String country, String state, String city,
                      LocalDate date, double averageTemperatureF) {
    public Reading {
        region = required(region, "Region");
        country = required(country, "Country");
        state = Objects.requireNonNullElse(state, "").trim();
        city = required(city, "City");
        Objects.requireNonNull(date, "Date is required");
        if (!Double.isFinite(averageTemperatureF)) {
            throw new IllegalArgumentException("Temperature must be finite");
        }
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }

    @Override
    public String toString() {
        String location = city + (state.isEmpty() ? "" : ", " + state) + ", " + country;
        return String.format(Locale.ROOT, "%s | %s | %.1f °F", date, location, averageTemperatureF);
    }
}
