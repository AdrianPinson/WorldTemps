package io.github.adrianpinson.worldtemps.workspace;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import io.github.adrianpinson.worldtemps.model.Reading;
import io.github.adrianpinson.worldtemps.service.DashboardQuery;
import io.github.adrianpinson.worldtemps.service.DashboardQuery.Location;
import io.github.adrianpinson.worldtemps.service.TemperatureAnalysis.Resolution;
import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;

/** Versioned portable snapshots; arbitrary files cannot trigger an API request or follow a stored path. */
public final class WorkspaceStore {
    private static final long MAX_BYTES = 64L * 1024 * 1024;
    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private WorkspaceStore() { }
    public record Row(String region, String country, String state, String city, String date, double temperatureF) {
        public Row {
            for(String value:List.of(region,country,state,city,date)) if(value.length()>256)throw new IllegalArgumentException("Workspace row text is too long.");
            new Reading(region,country,state,city,LocalDate.parse(date),temperatureF);
        }
        public static Row of(Reading r) { return new Row(r.region(),r.country(),r.state(),r.city(),r.date().toString(),r.averageTemperatureF()); }
        public Reading reading() { return new Reading(region,country,state,city,LocalDate.parse(date),temperatureF); }
    }
    public record Source(String kind, String label, String details) {
        public Source {
            if(!Set.of("WEATHER","CSV","SYNTHETIC").contains(kind) || label.isBlank() || label.length()>512 || details.length()>16000)
                throw new IllegalArgumentException("Invalid workspace provenance.");
        }
    }
    public record Settings(String region, String country, List<Location> cities, String from, String to,
                           Resolution resolution, String chartFrom, String chartTo) {
        public Settings {
            cities=List.copyOf(cities);Objects.requireNonNull(resolution);
            if(region.length()>256||country.length()>256||cities.size()>3||cities.stream().distinct().count()!=cities.size())
                throw new IllegalArgumentException("Invalid workspace selections.");
            new DashboardQuery.Filter(empty(region),empty(country),cities.isEmpty()?null:cities.get(0),date(from),date(to));
            if(date(chartFrom)!=null&&date(chartTo)!=null&&date(chartFrom).isAfter(date(chartTo)))
                throw new IllegalArgumentException("Invalid chart date range.");
        }
        public void validate(DashboardQuery query) {
            if(!region.isEmpty()&&!query.regions().contains(region) || !country.isEmpty()&&!query.countries(empty(region)).contains(country)
                    || !query.locations(empty(region),empty(country)).containsAll(cities))
                throw new IllegalArgumentException("Saved locations do not match the workspace dataset.");
        }
    }
    public record Document(int version, List<Row> readings, Settings settings, Source source) {
        public Document {
            readings=List.copyOf(readings);Objects.requireNonNull(settings);Objects.requireNonNull(source);
            if(version!=1||readings.isEmpty()||readings.size()>500000)throw new IllegalArgumentException("Use a version 1 workspace with 1–500,000 readings.");
        }
        public List<Reading> data() { return readings.stream().map(Row::reading).toList(); }
    }
    public static String text(LocalDate date) { return date==null?"":date.toString(); }
    public static LocalDate date(String text) { return text.isEmpty()?null:LocalDate.parse(text); }
    private static String empty(String text) { return text.isEmpty()?null:text; }
    public static void save(Path file, Document document) throws IOException {
        Path target=file.toAbsolutePath();Path temporary=Files.createTempFile(target.getParent(),".worldtemps-workspace-",".json");
        try {
            JSON.writeValue(temporary.toFile(),document);
            if(Files.size(temporary)>MAX_BYTES)throw new IOException("Workspace exceeds 64 MiB.");
            try { Files.move(temporary,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING); }
            catch(AtomicMoveNotSupportedException unsupported) { Files.move(temporary,target,StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
    }
    public static Document load(Path file) throws IOException {
        if(Files.size(file)>MAX_BYTES)throw new IOException("Workspace exceeds 64 MiB.");
        try(var stream=Files.newInputStream(file)) {
            byte[] bytes=stream.readNBytes((int)MAX_BYTES+1);
            if(bytes.length>MAX_BYTES)throw new IOException("Workspace exceeds 64 MiB.");
            return JSON.readValue(bytes,Document.class);
        } catch(IllegalArgumentException invalid) { throw new IOException("Invalid workspace: "+invalid.getMessage(),invalid); }
    }
}
