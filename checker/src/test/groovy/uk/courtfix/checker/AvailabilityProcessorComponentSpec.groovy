package uk.courtfix.checker

import spock.lang.Specification

import java.time.DayOfWeek
import java.time.LocalTime

class AvailabilityProcessorComponentSpec extends Specification {
    def "queues and delivers a newly bookable slot matching the saved weekly preferences"() {
        given:
        def subscription = new PushSubscription('iphone', 'https://push.example/endpoint', 'public-key', 'auth-key')
        def profile = new UserProfile('user-123', ['5147'] as Set,
                [new WeeklyWindow(DayOfWeek.MONDAY, LocalTime.parse('18:00'), LocalTime.parse('20:00'))],
                [subscription])
        def store = new MemoryStore([profile])
        def gateway = new RecordingGateway()
        def processor = new AvailabilityProcessor(store, gateway)
        def slot = new Slot('slot-123', '5147', 'Highbury Fields Court 1', '2026-10-12',
                '18:30', '19:30', 'BOOK', 1, '£10')

        when:
        def summary = processor.process([slot])

        then:
        summary.newNotifications() == 1
        summary.sent() == 1
        gateway.deliveries.size() == 1
        gateway.deliveries[0].slot().id() == 'slot-123'
        store.pending.isEmpty()
        store.active == ['slot-123'] as Set
    }

    private static class MemoryStore implements AvailabilityStore {
        List<UserProfile> users
        Set<String> active = [] as Set
        Map<String, PendingPush> pending = [:]

        MemoryStore(List<UserProfile> users) { this.users = users }
        List<UserProfile> loadUsers() { users }
        Set<String> loadActiveSlotIds() { new HashSet<>(active) }
        void updateAvailability(Set<Slot> current, Set<String> previous, List<PendingPush> notifications) {
            active.removeAll(previous)
            current.each { active.add(it.id()) }
            notifications.each { pending[it.id()] = it }
        }
        List<PendingPush> loadPendingPushes() { new ArrayList<>(pending.values()) }
        void removePendingPush(String id) { pending.remove(id) }
        void expireSubscription(String uid, String subscriptionId, String pushId) { pending.remove(pushId) }
    }

    private static class RecordingGateway implements PushGateway {
        List<PendingPush> deliveries = []
        DeliveryResult send(PushSubscription subscription, Slot slot, String notificationId) {
            deliveries << new PendingPush(notificationId, 'user-123', subscription, slot)
            DeliveryResult.SENT
        }
    }
}
