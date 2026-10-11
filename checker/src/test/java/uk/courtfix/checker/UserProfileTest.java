package uk.courtfix.checker;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class UserProfileTest {
    @Test
    void matchesCourtAndAtLeastOneWeeklyWindow() {
        UserProfile profile = new UserProfile("user-1", Set.of("5147"),
                List.of(new WeeklyWindow(DayOfWeek.MONDAY, LocalTime.of(18, 0), LocalTime.of(20, 0))), List.of());

        assertTrue(profile.matches(slot("5147", "2026-10-12", "18:30")));
        assertFalse(profile.matches(slot("5148", "2026-10-12", "18:30")));
        assertFalse(profile.matches(slot("5147", "2026-10-13", "18:30")));
    }

    private Slot slot(String court, String date, String time) {
        return new Slot("slot", court, "Court", date, time, "19:00", "BOOK", 1, "");
    }
}
