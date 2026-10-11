package uk.courtfix.checker;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CourtTest {
    @Test
    void storesCourtIdAndName() {
        Court court = new Court("5147", "Highbury Fields Court 1");

        assertEquals("5147", court.id());
        assertEquals("Highbury Fields Court 1", court.name());
    }
}
