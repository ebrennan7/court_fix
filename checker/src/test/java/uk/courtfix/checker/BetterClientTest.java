package uk.courtfix.checker;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BetterClientTest {
    private final LocalDate date = LocalDate.of(2026, 10, 12);

    @Test
    void parsesSlotFieldsAndUsesDefaultsForOptionalFields() throws Exception {
        String body = """
                {"data":[
                  {"id":"slot-1","location":{"id":"5147","name":"Court 1"},
                   "date":{"raw":"2026-10-12"},"starts_at":{"format_24_hour":"09:00"},
                   "ends_at":{"format_24_hour":"10:00"},"action_to_show":{"status":"BOOK"},
                   "spaces":2,"price":{"formatted":"£10.00"}},
                  {"id":"slot-2","location":{},"action_to_show":{}}
                ]}
                """;

        List<Slot> slots = BetterClient.parseSlots(body, date);

        assertEquals(2, slots.size());
        assertEquals(new Slot("slot-1", "5147", "Court 1", "2026-10-12", "09:00", "10:00",
                "BOOK", 2, "£10.00"), slots.get(0));
        assertEquals("Unknown court", slots.get(1).courtName());
        assertEquals("2026-10-12", slots.get(1).date());
        assertEquals("UNKNOWN", slots.get(1).status());
        assertFalse(slots.get(1).isBookable());
    }

    @Test
    void rejectsPayloadWithoutDataArray() {
        org.junit.jupiter.api.Assertions.assertThrows(java.io.IOException.class,
                () -> BetterClient.parseSlots("{\"data\":{}}", date));
    }
}
