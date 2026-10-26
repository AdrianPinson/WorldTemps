package io.github.adrianpinson.worldtemps.weather;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HttpWeatherTransportTest {
    private HttpServer server;
    private final AtomicInteger calls = new AtomicInteger();
    private final List<Long> delays = new ArrayList<>();

    private URI serve(java.util.function.IntUnaryOperator status, long sleep) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            int count = calls.incrementAndGet();
            try {
                if (sleep > 0) { Thread.sleep(sleep); }
                byte[] bytes = "{\"ok\":true}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Retry-After", "60");
                exchange.sendResponseHeaders(status.applyAsInt(count), bytes.length);
                exchange.getResponseBody().write(bytes);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (IOException ignored) { /* A timed-out test client may close the connection. */ }
            finally { exchange.close(); }
        });
        server.start();
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/");
    }

    @AfterEach void stop() { if (server != null) { server.stop(0); } }

    @Test void retriesOneTransientServerFailureAndReturnsTheSuccessfulBody() throws Exception {
        URI endpoint = serve(count -> count == 1 ? 503 : 200, 0);
        String json = new HttpWeatherTransport(HttpClient.newHttpClient(), Duration.ofSeconds(3), delays::add).get(endpoint);
        assertTrue(json.contains("true"));
        assertEquals(2, calls.get());
        assertEquals(List.of(2000L), delays);
    }

    @Test void rateLimitHasOneBoundedRetryAndAnActionableError() throws Exception {
        URI endpoint = serve(count -> 429, 0);
        var error = assertThrows(HttpWeatherTransport.HttpFailure.class,
                () -> new HttpWeatherTransport(HttpClient.newHttpClient(), Duration.ofSeconds(3), delays::add).get(endpoint));
        assertEquals(429, error.status());
        assertTrue(error.retryable());
        assertEquals(2, calls.get());
        assertEquals(List.of(2000L), delays);
    }

    @Test void clientErrorsAreNotRetried() throws Exception {
        URI endpoint = serve(count -> 400, 0);
        var error = assertThrows(HttpWeatherTransport.HttpFailure.class,
                () -> new HttpWeatherTransport(HttpClient.newHttpClient(), Duration.ofSeconds(3), delays::add).get(endpoint));
        assertFalse(error.retryable());
        assertEquals(1, calls.get());
        assertTrue(delays.isEmpty());
    }

    @Test void slowResponsesRespectTheRequestTimeout() throws Exception {
        URI endpoint = serve(count -> 200, 250);
        assertThrows(java.net.http.HttpTimeoutException.class,
                () -> new HttpWeatherTransport(HttpClient.newHttpClient(), Duration.ofMillis(30), delays::add).get(endpoint));
    }
}
