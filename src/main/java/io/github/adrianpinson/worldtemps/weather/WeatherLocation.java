package io.github.adrianpinson.worldtemps.weather;

import java.time.ZoneId;
import java.util.Objects;

/** A selected GeoNames result, including coordinates to disambiguate city names. */
public record WeatherLocation(long id, String city, String state, String country,
                              double latitude, double longitude, String timezone) {
    public WeatherLocation {
        city = required(city);
        state = Objects.requireNonNullElse(state, "").trim();
        country = required(country);
        timezone = required(timezone);
        ZoneId.of(timezone);
        if (id <= 0 || !Double.isFinite(latitude) || !Double.isFinite(longitude)
                || latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException("Invalid location coordinates or identifier.");
        }
    }

    private static String required(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Location name, country, and timezone are required.");
        }
        return value.trim();
    }

    /** The first IANA timezone component, not an inferred continent. */
    public String region() {
        return timezone.contains("/") ? timezone.substring(0, timezone.indexOf('/')) : timezone;
    }

    @Override public String toString() {
        return city + (state.isBlank() ? "" : ", " + state) + " · " + country;
    }
}
