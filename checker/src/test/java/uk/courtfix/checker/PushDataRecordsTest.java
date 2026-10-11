package uk.courtfix.checker;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class PushDataRecordsTest {
    @Test
    void pushSubscriptionKeepsBrowserEndpointAndEncryptionKeys() {
        PushSubscription subscription = new PushSubscription("device-1", "https://push.example/endpoint", "p256dh", "auth");

        assertEquals("device-1", subscription.id());
        assertEquals("https://push.example/endpoint", subscription.endpoint());
        assertEquals("p256dh", subscription.p256dh());
        assertEquals("auth", subscription.auth());
    }

    @Test
    void pendingPushConnectsUserSubscriptionAndSlot() {
        PushSubscription subscription = new PushSubscription("device-1", "https://push.example/endpoint", "p256dh", "auth");
        Slot slot = new Slot("slot-1", "5147", "Court 1", "2026-10-12", "18:00", "19:00", "BOOK", 1, "£10");
        PendingPush pending = new PendingPush("push-1", "user-1", subscription, slot);

        assertEquals("push-1", pending.id());
        assertEquals("user-1", pending.uid());
        assertSame(subscription, pending.subscription());
        assertSame(slot, pending.slot());
    }
}
