package uk.courtfix.checker;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FirebaseAvailabilityStoreTest {
    @Test
    void readsCourtIdsFromFirestoreListAndIgnoresBlankValues() {
        assertEquals(Set.of("5147", "5148"), FirebaseAvailabilityStore.strings(List.of("5147", "", "5148")));
        assertTrue(FirebaseAvailabilityStore.strings("not-a-list").isEmpty());
    }

    @Test
    void mapsFirestoreWindowRowsToWeeklyWindows() {
        var windows = FirebaseAvailabilityStore.windows(List.of(Map.of(
                "dayOfWeek", "WEDNESDAY", "startTime", "17:00", "endTime", "19:00")));

        assertEquals(1, windows.size());
        assertEquals("WEDNESDAY", windows.getFirst().dayOfWeek().name());
        assertEquals("17:00", windows.getFirst().startTime().toString());
    }

    @Test
    void returnsNoWindowsForMalformedContainerShape() {
        assertTrue(FirebaseAvailabilityStore.windows(Map.of()).isEmpty());
    }

    @Test
    void skipsMalformedWindowRowsAndKeepsValidRows() {
        var windows = FirebaseAvailabilityStore.windows(List.of(
                Map.of("dayOfWeek", "MONDAY", "startTime", "18:00", "endTime", "20:00"),
                Map.of("dayOfWeek", "NOT_A_DAY", "startTime", "18:00", "endTime", "20:00")));

        assertEquals(1, windows.size());
    }
}
