package uk.courtfix.checker;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MainTest {
    @TempDir
    Path tempDir;

    @Test
    void commandLineDateTakesPrecedenceOverEnvironmentAndDefault() {
        assertEquals(LocalDate.of(2026, 10, 14),
                Main.parseDate(new String[]{"--date=2026-10-14"}, LocalDate.of(2026, 10, 11), "2026-10-12"));
    }

    @Test
    void environmentDateIsUsedWhenNoCommandLineDateIsProvided() {
        assertEquals(LocalDate.of(2026, 10, 12),
                Main.parseDate(new String[0], LocalDate.of(2026, 10, 11), "2026-10-12"));
        assertEquals(LocalDate.of(2026, 10, 11),
                Main.parseDate(new String[0], LocalDate.of(2026, 10, 11), " "));
    }

    @Test
    void loadsCourtConfiguration() throws Exception {
        Path config = tempDir.resolve("courts.json");
        Files.writeString(config, "[{\"id\":\"5147\",\"name\":\"Court 1\"}]");

        assertEquals(List.of(new Court("5147", "Court 1")), Main.loadCourts(config));
    }

    @Test
    void rejectsInvalidCourtConfiguration() throws Exception {
        Path config = tempDir.resolve("courts.json");
        Files.writeString(config, "not json");

        assertThrows(Exception.class, () -> Main.loadCourts(config));
    }
}
