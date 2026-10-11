package uk.courtfix.checker;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AvailabilityProcessorTest {
    private static final Slot SLOT = new Slot("s-1", "5147", "Court 1", "2026-10-12",
            "18:30", "19:30", "BOOK", 1, "£10");
    private static final PushSubscription SUBSCRIPTION = new PushSubscription("device-1", "https://push.example/1", "p256dh", "auth");
    private static final UserProfile PROFILE = new UserProfile("user-1", Set.of("5147"),
            List.of(new WeeklyWindow(DayOfWeek.MONDAY, LocalTime.of(18, 0), LocalTime.of(20, 0))), List.of(SUBSCRIPTION));

    @Test
    void sendsOnlyForNewMatchingAvailableSlotAndAvoidsRepeatOnFollowingRun() throws Exception {
        FakeStore store = new FakeStore(List.of(PROFILE));
        RecordingGateway gateway = new RecordingGateway(PushGateway.DeliveryResult.SENT);
        AvailabilityProcessor processor = new AvailabilityProcessor(store, gateway);

        var first = processor.process(List.of(SLOT));
        var second = processor.process(List.of(SLOT));

        assertEquals(1, first.newNotifications());
        assertEquals(1, first.sent());
        assertEquals(0, second.newNotifications());
        assertEquals(0, second.sent());
        assertEquals(1, gateway.attempted.size());
    }

    @Test
    void retainsTransientFailuresForRetryOnNextRun() throws Exception {
        FakeStore store = new FakeStore(List.of(PROFILE));
        RecordingGateway gateway = new RecordingGateway(PushGateway.DeliveryResult.RETRY, PushGateway.DeliveryResult.SENT);
        AvailabilityProcessor processor = new AvailabilityProcessor(store, gateway);

        var first = processor.process(List.of(SLOT));
        var second = processor.process(List.of(SLOT));

        assertEquals(1, first.retrying());
        assertEquals(1, second.sent());
        assertEquals(2, gateway.attempted.size());
    }

    @Test
    void removesAQueuedNotificationWhenTheSlotIsNoLongerBookable() throws Exception {
        FakeStore store = new FakeStore(List.of(PROFILE));
        RecordingGateway gateway = new RecordingGateway(PushGateway.DeliveryResult.RETRY);
        AvailabilityProcessor processor = new AvailabilityProcessor(store, gateway);

        processor.process(List.of(SLOT));
        var second = processor.process(List.of());

        assertEquals(0, second.sent());
        assertEquals(1, gateway.attempted.size());
        assertEquals(0, store.pendingCount());
    }

    @Test
    void removesAnExpiredBrowserSubscription() throws Exception {
        FakeStore store = new FakeStore(List.of(PROFILE));
        RecordingGateway gateway = new RecordingGateway(PushGateway.DeliveryResult.EXPIRED);

        var summary = new AvailabilityProcessor(store, gateway).process(List.of(SLOT));

        assertEquals(1, summary.expiredSubscriptions());
        assertEquals(0, store.pendingCount());
    }

    @Test
    void doesNotNotifyForUnbookableOrNonmatchingSlots() throws Exception {
        UserProfile wrongCourt = new UserProfile("user-1", Set.of("5148"), PROFILE.windows(), PROFILE.subscriptions());
        FakeStore store = new FakeStore(List.of(wrongCourt));
        RecordingGateway gateway = new RecordingGateway(PushGateway.DeliveryResult.SENT);
        Slot unavailable = new Slot("s-2", "5147", "Court 1", "2026-10-12", "18:30", "19:30", "UNAVAILABLE", 0, "");

        var summary = new AvailabilityProcessor(store, gateway).process(List.of(unavailable, SLOT));

        assertEquals(0, summary.newNotifications());
        assertEquals(0, gateway.attempted.size());
    }

    private static final class FakeStore implements AvailabilityStore {
        private final List<UserProfile> users;
        private final Set<String> active = new HashSet<>();
        private final Map<String, PendingPush> pending = new LinkedHashMap<>();

        private FakeStore(List<UserProfile> users) { this.users = users; }
        public List<UserProfile> loadUsers() { return users; }
        public Set<String> loadActiveSlotIds() { return Set.copyOf(active); }
        public void updateAvailability(Set<Slot> current, Set<String> previous, List<PendingPush> notifications) {
            active.removeAll(previous);
            current.forEach(slot -> active.add(slot.id()));
            notifications.forEach(push -> pending.put(push.id(), push));
        }
        public List<PendingPush> loadPendingPushes() { return List.copyOf(pending.values()); }
        public void removePendingPush(String id) { pending.remove(id); }
        public void expireSubscription(String uid, String subscriptionId, String pushId) { pending.remove(pushId); }
        private int pendingCount() { return pending.size(); }
    }

    private static final class RecordingGateway implements PushGateway {
        private final List<PendingPush> attempted = new ArrayList<>();
        private final List<DeliveryResult> results;
        private int index;

        private RecordingGateway(DeliveryResult... results) { this.results = List.of(results); }
        public DeliveryResult send(PushSubscription subscription, Slot slot, String notificationId) {
            attempted.add(new PendingPush(notificationId, "user-1", subscription, slot));
            return results.get(Math.min(index++, results.size() - 1));
        }
    }
}
