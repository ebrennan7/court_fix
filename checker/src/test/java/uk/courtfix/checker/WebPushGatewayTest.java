package uk.courtfix.checker;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WebPushGatewayTest {
    @Test
    void preparesPayloadWithSlotDetailsAndBookingLink() {
        Slot slot = new Slot("s-1", "5147", "Highbury Fields Court 1", "2026-10-12",
                "18:00", "19:00", "BOOK", 1, "£10");

        Map<String, Object> payload = WebPushGateway.payloadFor(slot, "notification-1");

        assertEquals("A Highbury Fields court is available", payload.get("title"));
        assertEquals("Highbury Fields Court 1 on Mon 12 Oct, 18:00–19:00 is bookable now.", payload.get("body"));
        assertEquals("https://bookings.better.org.uk/location/islington-tennis-centre/highbury-tennis", payload.get("url"));
        assertEquals("notification-1", payload.get("tag"));
    }
}
