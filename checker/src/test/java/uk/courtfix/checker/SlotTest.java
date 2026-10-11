package uk.courtfix.checker;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlotTest {
    @Test
    void bookableWhenStatusIsBookAndSpacesRemain() {
        assertTrue(slot("BOOK", 1).isBookable());
        assertTrue(slot("book", 2).isBookable());
    }

    @Test
    void notBookableWithoutBookStatusOrSpaces() {
        assertFalse(slot("BOOK", 0).isBookable());
        assertFalse(slot("FULL", 3).isBookable());
        assertFalse(slot("UNKNOWN", 3).isBookable());
    }

    private Slot slot(String status, int spaces) {
        return new Slot("slot", "5147", "Court 1", "2026-10-12", "09:00", "10:00", status, spaces, "£10");
    }
}
