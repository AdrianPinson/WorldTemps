package io.github.adrianpinson.worldtemps.weather;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.adrianpinson.worldtemps.model.Reading;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/** Open-Meteo GeoNames search and ERA5 daily means, with validated exact-request caching. */
public final class OpenMeteoClient {
    public static final LocalDate EARLIEST = LocalDate.of(1940, 1, 1);
    public static final int MAX_DAYS = 3660;
    private static final Duration WEATHER_TTL = Duration.ofDays(7);
    private static final Duration SEARCH_TTL = Duration.ofDays(30);
    private static final ObjectMapper JSON = new ObjectMapper();
    private final WeatherTransport transport;
    private final WeatherCache cache;
    private final Clock clock;

    public OpenMeteoClient() {
        this(new HttpWeatherTransport(), new WeatherCache(Path.of(System.getProperty("user.home"), ".worldtemps", "weather-cache")), Clock.systemUTC());
    }

    public OpenMeteoClient(WeatherTransport transport, WeatherCache cache, Clock clock) {
        this.transport = Objects.requireNonNull(transport);
        this.cache = Objects.requireNonNull(cache);
        this.clock = Objects.requireNonNull(clock);
    }

    public LocalDate latestDate() {
        // ERA5 is published with a five-day delay; keep one additional day of margin.
        return LocalDate.now(clock.withZone(ZoneOffset.UTC)).minusDays(6);
    }

    public void validateDates(LocalDate start, LocalDate end) {
        if (start == null || end == null) {
            throw new IllegalArgumentException("Choose both a start and an end date.");
        }
        if (start.isBefore(EARLIEST) || start.isAfter(end) || end.isAfter(latestDate())) {
            throw new IllegalArgumentException("Choose dates from 1940-01-01 through " + latestDate() + ", with the start before the end.");
        }
        if (ChronoUnit.DAYS.between(start, end) + 1 > MAX_DAYS) {
            throw new IllegalArgumentException("Load at most 3,660 days at a time (about ten years).");
        }
    }

    public List<WeatherLocation> search(String name) throws IOException, InterruptedException {
        if (name == null || name.strip().length() < 2 || name.strip().length() > 100) {
            throw new IllegalArgumentException("Enter a city name between 2 and 100 characters.");
        }
        String key = "https://geocoding-api.open-meteo.com/v1/search?name=" + encode(name.strip())
                + "&count=10&language=en&format=json";
        Optional<WeatherCache.Entry> entry = validCache(key, json -> parseLocations(json));
        if (entry.isPresent() && fresh(entry.get(), SEARCH_TTL)) {
            return parseLocations(entry.get().json());
        }
        try {
            String json = transport.get(URI.create(key));
            List<WeatherLocation> locations = parseLocations(json);
            save(key, json);
            return locations;
        } catch (IOException e) {
            if (entry.isPresent() && canFallBack(e)) {
                return parseLocations(entry.get().json());
            }
            throw e;
        }
    }

    public Dataset load(List<WeatherLocation> locations, LocalDate start, LocalDate end) throws IOException, InterruptedException {
        validateDates(start, end);
        List<WeatherLocation> unique = locations.stream().distinct().toList();
        if (unique.isEmpty() || unique.size() > 3) {
            throw new IllegalArgumentException("Choose one to three cities.");
        }
        HashSet<String> names = new HashSet<>();
        for (WeatherLocation location : unique) {
            String identity = (location.country() + "\n" + location.state() + "\n" + location.city()).toLowerCase(Locale.ROOT);
            if (!names.add(identity)) {
                throw new IllegalArgumentException("Two selected locations share the same city, state, and country label. Choose only one.");
            }
        }
        List<CityWeather> cities = new ArrayList<>();
        for (WeatherLocation location : unique) {
            String request = historyRequest(location, start, end).toString();
            Optional<WeatherCache.Entry> entry = validCache(request, json -> parseDaily(json, location, start, end));
            if (entry.isPresent() && fresh(entry.get(), WEATHER_TTL)) {
                Parsed parsed = parseDaily(entry.get().json(), location, start, end);
                cities.add(city(location, parsed, Origin.FRESH_CACHE, entry.get().fetchedAt(), true));
                continue;
            }
            String json;
            try {
                json = transport.get(URI.create(request));
            } catch (IOException e) {
                if (entry.isPresent() && canFallBack(e)) {
                    Parsed parsed = parseDaily(entry.get().json(), location, start, end);
                    cities.add(city(location, parsed, Origin.OFFLINE_CACHE, entry.get().fetchedAt(), true));
                    continue;
                }
                throw e;
            }
            Parsed parsed = parseDaily(json, location, start, end);
            Instant fetchedAt = clock.instant();
            cities.add(city(location, parsed, Origin.NETWORK, fetchedAt, save(request, json)));
        }
        return new Dataset(cities);
    }

    static URI historyRequest(WeatherLocation location, LocalDate start, LocalDate end) {
        return URI.create("https://archive-api.open-meteo.com/v1/archive?latitude=" + location.latitude()
                + "&longitude=" + location.longitude() + "&start_date=" + start + "&end_date=" + end
                + "&daily=temperature_2m_mean&temperature_unit=fahrenheit&timezone=" + encode(location.timezone()) + "&models=era5");
    }

    private static String encode(String text) { return URLEncoder.encode(text, StandardCharsets.UTF_8); }

    private Optional<WeatherCache.Entry> validCache(String key, Validator validator) {
        return cache.read(key).filter(entry -> {
            try { validator.check(entry.json()); return true; }
            catch (IOException | RuntimeException e) { return false; }
        });
    }

    private boolean fresh(WeatherCache.Entry entry, Duration ttl) {
        Duration age = Duration.between(entry.fetchedAt(), clock.instant());
        return !age.isNegative() && age.compareTo(ttl) < 0;
    }

    private boolean save(String request, String json) {
        try { cache.write(request, json, clock.instant()); return true; }
        catch (IOException | RuntimeException e) { return false; }
    }

    private static boolean canFallBack(IOException error) {
        return !(error instanceof InvalidPayload) && (!(error instanceof HttpWeatherTransport.HttpFailure failure) || failure.retryable());
    }

    static List<WeatherLocation> parseLocations(String json) throws IOException {
        JsonNode root = object(json);
        JsonNode results = root.get("results");
        if (results == null || results.isNull()) { return List.of(); }
        if (!results.isArray()) { throw new InvalidPayload("Open-Meteo returned an invalid city list."); }
        List<WeatherLocation> locations = new ArrayList<>();
        try {
            for (JsonNode node : results) {
                if (!node.path("id").isIntegralNumber() || !node.path("latitude").isNumber() || !node.path("longitude").isNumber()) {
                    throw new IllegalArgumentException("Location identifier or coordinates are missing.");
                }
                locations.add(new WeatherLocation(node.get("id").asLong(), text(node, "name"),
                        node.path("admin1").asText(""), text(node, "country"), node.get("latitude").asDouble(),
                        node.get("longitude").asDouble(), node.path("timezone").asText("UTC")));
            }
        } catch (RuntimeException e) {
            throw new InvalidPayload("Open-Meteo returned invalid location details.", e);
        }
        return locations.stream().distinct().toList();
    }

    static Parsed parseDaily(String json, WeatherLocation location, LocalDate start, LocalDate end) throws IOException {
        JsonNode root = object(json);
        JsonNode dates = root.path("daily").path("time");
        JsonNode temperatures = root.path("daily").path("temperature_2m_mean");
        String unit = root.path("daily_units").path("temperature_2m_mean").asText();
        if (!dates.isArray() || !temperatures.isArray() || dates.size() != temperatures.size()
                || !(unit.equals("°F") || unit.equals("°C"))) {
            throw new InvalidPayload("Open-Meteo returned missing daily values or unsupported temperature units.");
        }
        List<Reading> readings = new ArrayList<>();
        HashSet<LocalDate> seen = new HashSet<>();
        try {
            for (int index = 0; index < dates.size(); index++) {
                if (!dates.get(index).isTextual()) { throw new IllegalArgumentException("Invalid date."); }
                LocalDate date = LocalDate.parse(dates.get(index).asText());
                if (date.isBefore(start) || date.isAfter(end) || !seen.add(date)) {
                    throw new IllegalArgumentException("Unexpected or duplicate date.");
                }
                JsonNode value = temperatures.get(index);
                if (value.isNull()) { continue; }
                if (!value.isNumber() || !Double.isFinite(value.asDouble())) {
                    throw new IllegalArgumentException("Invalid temperature.");
                }
                double temperature = unit.equals("°C") ? value.asDouble() * 9.0 / 5.0 + 32 : value.asDouble();
                readings.add(new Reading(location.region(), location.country(), location.state(), location.city(), date, temperature));
            }
        } catch (RuntimeException e) {
            throw new InvalidPayload("Open-Meteo returned invalid dates or daily temperatures.", e);
        }
        if (readings.isEmpty()) { throw new InvalidPayload("No daily temperatures are available for that location and range."); }
        int missing = (int) (ChronoUnit.DAYS.between(start, end) + 1 - readings.size());
        return new Parsed(List.copyOf(readings), missing);
    }

    private static JsonNode object(String json) throws IOException {
        try {
            JsonNode node = JSON.readTree(json);
            if (node == null || !node.isObject() || node.path("error").asBoolean(false)) {
                throw new InvalidPayload("Open-Meteo returned an error or invalid JSON object.");
            }
            return node;
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new InvalidPayload("Open-Meteo returned malformed JSON.", e);
        }
    }

    private static String text(JsonNode node, String field) {
        if (!node.path(field).isTextual() || node.path(field).asText().isBlank()) {
            throw new IllegalArgumentException("Missing location " + field);
        }
        return node.get(field).asText();
    }

    private static CityWeather city(WeatherLocation location, Parsed parsed, Origin origin, Instant fetchedAt, boolean saved) {
        return new CityWeather(location, parsed.readings(), parsed.missing(), origin, fetchedAt, saved);
    }

    record Parsed(List<Reading> readings, int missing) { }
    @FunctionalInterface private interface Validator { void check(String json) throws IOException; }
    public enum Origin { NETWORK, FRESH_CACHE, OFFLINE_CACHE }
    public record CityWeather(WeatherLocation location, List<Reading> readings, int missingDays,
                              Origin origin, Instant fetchedAt, boolean cacheSaved) {
        public CityWeather { readings = List.copyOf(readings); }
    }
    public record Dataset(List<CityWeather> cities) {
        public Dataset { cities = List.copyOf(cities); }
        public List<Reading> readings() { return cities.stream().flatMap(city -> city.readings().stream()).toList(); }
        public int missingDays() { return cities.stream().mapToInt(CityWeather::missingDays).sum(); }
    }
    private static final class InvalidPayload extends IOException {
        InvalidPayload(String message) { super(message); }
        InvalidPayload(String message, Throwable cause) { super(message, cause); }
    }
}
