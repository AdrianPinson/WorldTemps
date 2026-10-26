package io.github.adrianpinson.worldtemps;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class WorldTempsTest {
    @TempDir Path temporary;

    private Result run(String input, String... args) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int status = WorldTemps.run(args, new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)),
                new PrintStream(out, true, StandardCharsets.UTF_8), new PrintStream(err, true, StandardCharsets.UTF_8));
        return new Result(status, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
    }

    @Test void bundledSampleSupportsEveryMenuOperationAndInvalidInput() {
        Result result = run("nonsense\n1\n2\n3\nUK\n4\nEurope\n5\nUS\nTexas\nLongview\n6\nUS\nTexas\nLongview\n0\n");
        assertEquals(0, result.status());
        assertEquals("", result.error());
        assertTrue(result.output().contains("synthetic demo"));
        assertTrue(result.output().contains("Loaded 270 readings; rejected 0"));
        assertTrue(result.output().contains("Invalid option"));
        assertTrue(result.output().contains("London, UK | 37.0 °F"));
        assertTrue(result.output().contains("Longview, Texas, US | 89.0 °F"));
        assertTrue(result.output().contains("Relative bars"));
        assertTrue(result.output().contains("Goodbye!"));
    }

    @Test void endOfInputExitsCleanly() {
        assertEquals(0, run("").status());
        assertEquals(0, run("6\nUS\n").status());
    }

    @Test void helpDoesNotOpenData() {
        assertTrue(run("", "--help").output().contains("Usage:"));
        assertEquals(0, run("", "--help").status());
    }

    @Test void missingFileIsAnErrorWithNoStackTrace() {
        Result result = run("", temporary.resolve("missing.csv").toString());
        assertEquals(1, result.status());
        assertTrue(result.error().contains("Unable to load"));
        assertFalse(result.error().contains("Exception"));
    }

    @Test void rejectsExtraArguments() {
        assertEquals(2, run("", "one.csv", "two.csv").status());
    }

    @Test void emptyAndMalformedImportsReturnFailure() throws Exception {
        Path csv = temporary.resolve("empty.csv");
        Files.writeString(csv, "Region,Country,State,City,Month,Day,Year,AvgTemperature\n");
        assertEquals(1, run("", csv.toString()).status());
        Files.writeString(csv, "bad,header\n");
        assertEquals(1, run("", csv.toString()).status());
    }

    @Test void userFileReportsRejectedRowsAndStillSupportsQueries() throws Exception {
        Path csv = temporary.resolve("data.csv");
        Files.writeString(csv, "Region,Country,State,City,Month,Day,Year,AvgTemperature\n"
                + "Europe,UK,,London,1,1,2025,-99\nEurope,UK,,London,1,2,2025,40\n");
        Result result = run("1\n0\n", csv.toString());
        assertEquals(0, result.status());
        assertTrue(result.output().contains("Loaded 1 readings; rejected 1"));
        assertTrue(result.output().contains("Record 2:"));
        assertTrue(result.output().contains("40.0 °F"));
    }

    private record Result(int status, String output, String error) { }
}
