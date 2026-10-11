package uk.courtfix.checker;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class WeeklyWindowTest {
    private final WeeklyWindow window = new WeeklyWindow(DayOfWeek.MONDAY,
            LocalTime.of(18, 0), LocalTime.of(20, 0));

    @Test
    void matchesSlotStartingInsideSelectedDayAndTimeRange() {
        assertTrue(window.matches(slot("2026-10-12", "18:00")));
        assertTrue(window.matches(slot("2026-10-12", "19:59")));
        assertFalse(window.matches(slot("2026-10-12", "20:00")));
        assertFalse(window.matches(slot("2026-10-13", "19:00")));
    }

    @Test
    void parsesSavedFirestoreWindowShape() {
        assertEquals(window, WeeklyWindow.fromMap(Map.of(
                "dayOfWeek", "MONDAY", "startTime", "18:00", "endTime", "20:00")));
    }

    @Test
    void rejectsNonIncreasingTimes() {
        assertThrows(IllegalArgumentException.class, () -> new WeeklyWindow(DayOfWeek.MONDAY,
                LocalTime.of(20, 0), LocalTime.of(18, 0)));
    }

    private Slot slot(String date, String time) {
        return new Slot("slot", "5147", "Court 1", date, time, "21:00", "BOOK", 1, "");
    }
}
