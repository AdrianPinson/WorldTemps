package io.github.adrianpinson.worldtemps.workspace;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import io.github.adrianpinson.worldtemps.service.DashboardQuery;
import io.github.adrianpinson.worldtemps.service.TemperatureData;
import io.github.adrianpinson.worldtemps.service.TemperatureAnalysis.Resolution;

class WorkspaceStoreTest {
    @TempDir Path directory;
    WorkspaceStore.Document fixture() {
        return new WorkspaceStore.Document(1,List.of(new WorkspaceStore.Row("Asia","Japan","","東京","2020-02-29",-5)),
                new WorkspaceStore.Settings("","",List.of(new DashboardQuery.Location("Japan","","東京")),"2020-02-29","2020-02-29",Resolution.MONTHLY,"",""),
                new WorkspaceStore.Source("SYNTHETIC","Synthetic demo","Invented data; not observations."));
    }
    @Test void roundTripsUnicodeNegativeTemperaturesSettingsAndProvenance() throws Exception {
        Path file=directory.resolve("analysis.worldtemps");var expected=fixture();WorkspaceStore.save(file,expected);
        var loaded=WorkspaceStore.load(file);assertEquals(expected,loaded);
        loaded.settings().validate(new DashboardQuery(new TemperatureData(loaded.data())));
    }
    @Test void rejectsUnknownVersionsMissingFieldsDuplicateKeysAndTrailingContent() throws Exception {
        Path file=directory.resolve("invalid.worldtemps");WorkspaceStore.save(file,fixture());String valid=Files.readString(file);
        for(String invalid:List.of(valid.replace("\"version\":1","\"version\":2"),valid.replace("\"version\":1","\"version\":1,\"version\":1"),
                valid.replace(",\"temperatureF\":-5.0",""),valid+" {}",valid.replace("\"readings\":[","\"unknown\":1,\"readings\":["))) {
            // Explicit missing-field fixture does not depend on property ordering.
            if(invalid.equals(valid))continue;
            Files.writeString(file,invalid);assertThrows(java.io.IOException.class,()->WorkspaceStore.load(file));
        }
        Files.writeString(file,"{\"version\":1,\"readings\":[]}");assertThrows(java.io.IOException.class,()->WorkspaceStore.load(file));
    }
    @Test void failedReplacementPreservesExistingDestinationAndCleansTemporaryFile() throws Exception {
        Path target=directory.resolve("existing-directory");Files.createDirectory(target);Files.writeString(target.resolve("keep.txt"),"keep");
        assertThrows(java.io.IOException.class,()->WorkspaceStore.save(target,fixture()));
        assertEquals("keep",Files.readString(target.resolve("keep.txt")));
        try(var files=Files.list(directory)) { assertEquals(1,files.count()); }
    }
    @Test void rejectsUnavailableSelectionsAndInvalidDates() {
        var query=new DashboardQuery(new TemperatureData(fixture().data()));
        var settings=new WorkspaceStore.Settings("","",List.of(new DashboardQuery.Location("US","Texas","Longview")),"","",Resolution.RAW,"","");
        assertThrows(IllegalArgumentException.class,()->settings.validate(query));
        assertThrows(java.time.DateTimeException.class,()->new WorkspaceStore.Row("A","B","","C","2021-02-29",0));
    }
}
