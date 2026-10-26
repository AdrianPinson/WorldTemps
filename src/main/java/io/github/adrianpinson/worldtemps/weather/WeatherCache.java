package io.github.adrianpinson.worldtemps.weather;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

/** Exact-request JSON cache. Unreadable/corrupt entries are misses, not application failures. */
public final class WeatherCache {
    private final Path directory;

    public WeatherCache(Path directory) {
        this.directory = directory;
    }

    public Optional<Entry> read(String request) {
        Path file = path(request);
        try {
            if (Files.size(file) > 5_000_100) {
                return Optional.empty();
            }
            String text = Files.readString(file, StandardCharsets.UTF_8);
            int split = text.indexOf('\n');
            if (split < 1) { return Optional.empty(); }
            return Optional.of(new Entry(Instant.parse(text.substring(0, split)), text.substring(split + 1)));
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        }
    }

    public void write(String request, String json, Instant fetchedAt) throws IOException {
        Files.createDirectories(directory);
        Path temporary = Files.createTempFile(directory, ".weather-", ".tmp");
        try {
            Files.writeString(temporary, fetchedAt + "\n" + json, StandardCharsets.UTF_8);
            try {
                Files.move(temporary, path(request), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, path(request), StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private Path path(String request) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(request.getBytes(StandardCharsets.UTF_8));
            return directory.resolve(HexFormat.of().formatHex(digest) + ".json-cache");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by Java.", e);
        }
    }

    public record Entry(Instant fetchedAt, String json) { }
}
