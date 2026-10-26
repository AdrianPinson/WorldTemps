package io.github.adrianpinson.worldtemps.weather;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

/** Invented responses following Open-Meteo's schema; no network access or real-weather claims. */
public final class WeatherFixtures {
    private WeatherFixtures() { }
    public static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-26T12:00:00Z"), ZoneOffset.UTC);
    public static final WeatherLocation LONGVIEW = new WeatherLocation(1, "Longview", "Texas", "United States", 32.5, -94.74, "America/Chicago");
    public static final WeatherLocation LONDON = new WeatherLocation(2, "London", "England", "United Kingdom", 51.5, -0.12, "Europe/London");
    public static final WeatherLocation TOKYO = new WeatherLocation(3, "Tokyo", "Tokyo", "Japan", 35.68, 139.69, "Asia/Tokyo");
    public static final List<WeatherLocation> CITIES = List.of(LONGVIEW, LONDON, TOKYO);
    public static final String GEOCODING = """
            {"results":[
              {"id":1,"name":"Longview","admin1":"Texas","country":"United States","latitude":32.5,"longitude":-94.74,"timezone":"America/Chicago"},
              {"id":2,"name":"London","admin1":"England","country":"United Kingdom","latitude":51.5,"longitude":-0.12,"timezone":"Europe/London"},
              {"id":3,"name":"Tokyo","admin1":"Tokyo","country":"Japan","latitude":35.68,"longitude":139.69,"timezone":"Asia/Tokyo"}
            ]}
            """;
    public static String daily(String unit, String values) {
        return """
                {"daily_units":{"temperature_2m_mean":"%s"},
                 "daily":{"time":["2025-01-01","2025-01-02","2025-01-03"],"temperature_2m_mean":[%s]}}
                """.formatted(unit, values);
    }
    public static final String DAILY = daily("°F", "45,47,53");
}
