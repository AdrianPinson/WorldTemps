package io.github.adrianpinson.worldtemps.weather;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static io.github.adrianpinson.worldtemps.weather.WeatherFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class OpenMeteoClientTest {
    @TempDir Path directory;
    private final LocalDate start = LocalDate.of(2025, 1, 1);
    private final LocalDate end = LocalDate.of(2025, 1, 3);
    private OpenMeteoClient client(WeatherTransport transport) {
        return new OpenMeteoClient(transport, new WeatherCache(directory), CLOCK);
    }

    @Test void searchesEncodeUnicodeAndDisambiguateResults() throws Exception {
        List<URI> requests = new ArrayList<>();
        var locations = client(uri -> { requests.add(uri); return GEOCODING; }).search("São José, Brazil");
        assertEquals(3, locations.size());
        assertTrue(requests.get(0).toASCIIString().contains("S%C3%A3o+Jos%C3%A9"));
        assertEquals("Texas", locations.get(0).state());
        assertEquals("America", locations.get(0).region());
        assertEquals(List.of(), OpenMeteoClient.parseLocations("{}"));
    }

    @Test void freshSearchCacheAvoidsDuplicateNetworkCalls() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        var api = client(uri -> { calls.incrementAndGet(); return GEOCODING; });
        assertEquals(api.search("Longview"), api.search("Longview"));
        assertEquals(1, calls.get());
    }

    @Test void requestContainsCoordinatesDatesUnitsTimezoneAndConsistentModel() throws Exception {
        List<URI> requests = new ArrayList<>();
        var data = client(uri -> { requests.add(uri); return DAILY; }).load(List.of(LONGVIEW), start, end);
        String request = requests.get(0).toString();
        assertTrue(request.contains("latitude=32.5"));
        assertTrue(request.contains("temperature_unit=fahrenheit"));
        assertTrue(request.contains("timezone=America%2FChicago"));
        assertTrue(request.contains("models=era5"));
        assertTrue(request.contains("start_date=2025-01-01&end_date=2025-01-03"));
        assertEquals(3, data.readings().size());
        assertEquals(45, data.readings().get(0).averageTemperatureF());
        assertEquals(OpenMeteoClient.Origin.NETWORK, data.cities().get(0).origin());
    }

    @Test void loadsThreeCitiesAndReusesValidatedDiskCacheAcrossClientInstances() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        var api = client(uri -> { calls.incrementAndGet(); return DAILY; });
        assertEquals(9, api.load(CITIES, start, end).readings().size());
        var cached = client(uri -> { throw new AssertionError("Fresh cache must avoid HTTP"); }).load(CITIES, start, end);
        assertEquals(3, calls.get());
        assertTrue(cached.cities().stream().allMatch(city -> city.origin() == OpenMeteoClient.Origin.FRESH_CACHE));
    }

    @Test void expiredCacheFallsBackOnNetworkFailureWithExplicitOfflineProvenance() throws Exception {
        client(uri -> DAILY).load(List.of(LONGVIEW), start, end);
        var offline = new OpenMeteoClient(uri -> { throw new IOException("Offline"); }, new WeatherCache(directory),
                Clock.offset(CLOCK, Duration.ofDays(8))).load(List.of(LONGVIEW), start, end);
        assertEquals(OpenMeteoClient.Origin.OFFLINE_CACHE, offline.cities().get(0).origin());
        assertEquals(CLOCK.instant(), offline.cities().get(0).fetchedAt());
    }

    @Test void expiredCacheDoesNotHideInvalidRequestsOrBadNewPayloads() throws Exception {
        client(uri -> DAILY).load(List.of(LONGVIEW), start, end);
        Clock expired = Clock.offset(CLOCK, Duration.ofDays(8));
        var denied = new OpenMeteoClient(uri -> { throw new HttpWeatherTransport.HttpFailure("Bad request", 400, false); }, new WeatherCache(directory), expired);
        assertThrows(IOException.class, () -> denied.load(List.of(LONGVIEW), start, end));
        var malformed = new OpenMeteoClient(uri -> "not json", new WeatherCache(directory), expired);
        assertThrows(IOException.class, () -> malformed.load(List.of(LONGVIEW), start, end));
    }

    @Test void cacheKeysDistinguishDateRangesAndCorruptEntriesAreRefetched() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        var api = client(uri -> { calls.incrementAndGet(); return DAILY; });
        api.load(List.of(LONGVIEW), start, end);
        try (var files = Files.list(directory)) { Files.writeString(files.findFirst().orElseThrow(), "broken cache"); }
        api.load(List.of(LONGVIEW), start, end);
        assertEquals(2, calls.get());
        assertNotEquals(OpenMeteoClient.historyRequest(LONGVIEW, start, end), OpenMeteoClient.historyRequest(LONGVIEW, start, end.plusDays(1)));
    }

    @Test void cacheWriteFailureDoesNotDiscardValidWeather() throws Exception {
        Path file = directory.resolve("not-a-directory");
        Files.writeString(file, "existing content");
        var data = new OpenMeteoClient(uri -> DAILY, new WeatherCache(file), CLOCK).load(List.of(LONGVIEW), start, end);
        assertEquals(3, data.readings().size());
        assertFalse(data.cities().get(0).cacheSaved());
        assertEquals("existing content", Files.readString(file));
    }

    @Test void missingTemperaturesAreCountedWithoutCreatingZerosAndCelsiusIsConverted() throws Exception {
        var parsed = OpenMeteoClient.parseDaily(daily("°C", "0,null,10"), LONGVIEW, start, end);
        assertEquals(2, parsed.readings().size());
        assertEquals(1, parsed.missing());
        assertEquals(32, parsed.readings().get(0).averageTemperatureF());
        assertEquals(50, parsed.readings().get(1).averageTemperatureF());
    }

    @Test void rejectsMalformedPayloadsUnexpectedUnitsDuplicateDatesAndAllMissingData() {
        for (String payload : List.of("not json", "{}", DAILY.replace("°F", "K"),
                DAILY.replace("2025-01-02", "2025-01-01"), DAILY.replace("45,47,53", "45,47"), daily("°F", "null,null,null"),
                DAILY.replace("2025-01-01", "2024-12-31"), DAILY.replace("45,47,53", "\"oops\",47,53"))) {
            assertThrows(IOException.class, () -> OpenMeteoClient.parseDaily(payload, LONGVIEW, start, end));
        }
        assertThrows(IOException.class, () -> OpenMeteoClient.parseLocations(GEOCODING.replace("32.5", "999")));
        assertThrows(IOException.class, () -> OpenMeteoClient.parseLocations("{\"error\":true}"));
    }

    @Test void validatesDatesAndSelectionBeforeAnyHttpCall() {
        var api = client(uri -> { throw new AssertionError("Invalid input must not call HTTP"); });
        assertEquals(LocalDate.of(2026, 10, 20), api.latestDate());
        assertThrows(IllegalArgumentException.class, () -> api.load(List.of(LONGVIEW), end, start));
        assertThrows(IllegalArgumentException.class, () -> api.load(List.of(LONGVIEW), start, LocalDate.of(2026, 10, 21)));
        assertThrows(IllegalArgumentException.class, () -> api.load(List.of(LONGVIEW), LocalDate.of(1939, 1, 1), end));
        assertThrows(IllegalArgumentException.class, () -> api.load(List.of(LONGVIEW), LocalDate.of(1940, 1, 1), end));
        assertThrows(IllegalArgumentException.class, () -> api.load(List.of(), start, end));
        assertThrows(IllegalArgumentException.class, () -> api.search("x"));
    }

    @Test void twoIdenticalDisplayNamesWithDifferentCoordinatesCannotSilentlyMerge() {
        WeatherLocation duplicateName = new WeatherLocation(99, LONGVIEW.city(), LONGVIEW.state(), LONGVIEW.country(), 33, -95, LONGVIEW.timezone());
        assertThrows(IllegalArgumentException.class, () -> client(uri -> DAILY).load(List.of(LONGVIEW, duplicateName), start, end));
    }

    @Test void interruptedRequestsDoNotFallBackToStaleCache() throws Exception {
        client(uri -> DAILY).load(List.of(LONGVIEW), start, end);
        var api = new OpenMeteoClient(uri -> { throw new InterruptedException("Cancelled"); }, new WeatherCache(directory),
                Clock.offset(CLOCK, Duration.ofDays(8)));
        assertThrows(InterruptedException.class, () -> api.load(List.of(LONGVIEW), start, end));
    }
}
