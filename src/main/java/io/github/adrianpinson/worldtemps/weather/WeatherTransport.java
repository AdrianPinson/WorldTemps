package io.github.adrianpinson.worldtemps.weather;

import java.io.IOException;
import java.net.URI;

@FunctionalInterface
public interface WeatherTransport {
    String get(URI uri) throws IOException, InterruptedException;
}
