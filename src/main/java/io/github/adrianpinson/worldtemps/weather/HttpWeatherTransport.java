package io.github.adrianpinson.worldtemps.weather;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/** One bounded retry for throttling/server failures; network work runs off the UI thread. */
public final class HttpWeatherTransport implements WeatherTransport {
    private final HttpClient client;
    private final Duration timeout;
    private final Sleeper sleeper;

    public HttpWeatherTransport() {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8))
                .followRedirects(HttpClient.Redirect.NEVER).build(), Duration.ofSeconds(15), Thread::sleep);
    }

    HttpWeatherTransport(HttpClient client, Duration timeout, Sleeper sleeper) {
        this.client = client;
        this.timeout = timeout;
        this.sleeper = sleeper;
    }

    @Override public String get(URI uri) throws IOException, InterruptedException {
        for (int attempt = 0; attempt < 2; attempt++) {
            HttpRequest request = HttpRequest.newBuilder(uri).timeout(timeout)
                    .header("Accept", "application/json").header("User-Agent", "WorldTemps/1.0 (educational desktop project)")
                    .GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int code = response.statusCode();
            if (code == 200) {
                if (response.body().length() > 5_000_000) {
                    throw new IOException("Weather response is too large. Choose a shorter date range.");
                }
                return response.body();
            }
            boolean retryable = code == 429 || code >= 500;
            if (retryable && attempt == 0) {
                long delay = 300;
                try {
                    delay = Math.min(2000, Math.max(0, Long.parseLong(response.headers().firstValue("Retry-After").orElse("0")) * 1000));
                } catch (NumberFormatException ignored) { /* Use the bounded default delay. */ }
                sleeper.sleep(Math.max(300, delay));
                continue;
            }
            String message = code == 429 ? "Open-Meteo is rate-limiting requests. Please try again later."
                    : "Open-Meteo returned HTTP " + code + ". Please check the selection or try again later.";
            throw new HttpFailure(message, code, retryable);
        }
        throw new IOException("Weather request failed.");
    }

    public static final class HttpFailure extends IOException {
        private final int status;
        private final boolean retryable;
        HttpFailure(String message, int status, boolean retryable) {
            super(message);
            this.status = status;
            this.retryable = retryable;
        }
        public int status() { return status; }
        public boolean retryable() { return retryable; }
    }

    @FunctionalInterface interface Sleeper { void sleep(long milliseconds) throws InterruptedException; }
}
