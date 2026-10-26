package io.github.adrianpinson.worldtemps.importer;

import java.io.IOException;
import java.io.StringReader;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CsvTemperatureLoaderTest {
    private static final String HEADER = "Region,Country,State,City,Month,Day,Year,AvgTemperature\n";
    private final CsvTemperatureLoader loader = new CsvTemperatureLoader();

    @Test void handlesQuotedCommasEscapedQuotesAndBlankState() throws IOException {
        var result = loader.load(new StringReader(HEADER
                + "Europe,UK,,\"London, \"\"Central\"\"\",1,1,2025,40\n"));
        assertEquals(1, result.data().size());
        assertEquals("London, \"Central\"", result.data().maximum().orElseThrow().city());
        assertEquals(0, result.rejectedRows());
    }

    @Test void supportsReorderedCaseInsensitiveHeaders() throws IOException {
        var result = loader.load(new StringReader("city,state,country,region,year,day,month,avgtemperature\n"
                + "London,,UK,Europe,2025,1,1,40\n"));
        assertEquals(1, result.data().size());
    }

    @Test void rejectsBadRowsButPreservesValidReadings() throws IOException {
        var result = loader.load(new StringReader(HEADER
                + "Europe,UK,,London,1,1,2025,40\n"
                + "Europe,UK,,London,2,30,2025,40\n"
                + "Europe,UK,,London,1,2,2025,-99\n"
                + "Europe,UK,,London,1,3,2025,NaN\n"
                + "Europe,UK,,London,1,4,2025,Infinity\n"
                + "Europe,UK,,London,1,5,2025,nope\n"
                + "Europe,UK,,,1,6,2025,40\n"
                + "Europe,UK,,London,1,7,2025\n"
                + "Europe,UK,,London,1,8,2025,41\n"));
        assertEquals(2, result.data().size());
        assertEquals(7, result.rejectedRows());
        assertEquals(3, result.issues().get(0).recordNumber());
        assertEquals(41, result.data().maximum().orElseThrow().averageTemperatureF());
    }

    @Test void acceptsNegativeTemperaturesAndLeapDay() throws IOException {
        var result = loader.load(new StringReader(HEADER + "Europe,UK,,London,2,29,2024,-5\n"));
        assertEquals(-5, result.data().minimum().orElseThrow().averageTemperatureF());
    }

    @Test void missingHeaderFailsWithActionableMessage() {
        IOException error = assertThrows(IOException.class, () -> loader.load(new StringReader("City,Year\nLondon,2025\n")));
        assertTrue(error.getMessage().contains("Missing required CSV column"));
    }

    @Test void malformedQuotingFailsTheImportInsteadOfPublishingPartialData() {
        assertThrows(IOException.class, () -> loader.load(new StringReader(HEADER
                + "Europe,UK,,London,1,1,2025,40\nEurope,UK,,\"unclosed,1,2,2025,40\n")));
    }

    @Test void headerOnlyFileProducesAnEmptyDataset() throws IOException {
        var result = loader.load(new StringReader(HEADER));
        assertEquals(0, result.data().size());
        assertEquals(0, result.rejectedRows());
    }

    @Test void capsDiagnosticsWithoutLosingRejectedCount() throws IOException {
        var result = loader.load(new StringReader(HEADER + "Europe,UK,,London,1,1,2025,-99\n".repeat(25)));
        assertEquals(25, result.rejectedRows());
        assertEquals(20, result.issues().size());
        assertThrows(UnsupportedOperationException.class, () -> result.issues().clear());
    }

    @Test void closesInputReader() throws IOException {
        boolean[] closed = {false};
        StringReader reader = new StringReader(HEADER) {
            @Override public void close() {
                closed[0] = true;
                super.close();
            }
        };
        loader.load(reader);
        assertTrue(closed[0]);
    }
}
