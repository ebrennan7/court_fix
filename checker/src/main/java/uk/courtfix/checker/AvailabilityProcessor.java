package uk.courtfix.checker;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class AvailabilityProcessor {
    private final AvailabilityStore store;
    private final PushGateway pushGateway;

    public AvailabilityProcessor(AvailabilityStore store, PushGateway pushGateway) {
        this.store = store;
        this.pushGateway = pushGateway;
    }

    public RunSummary process(List<Slot> observedSlots) throws IOException, InterruptedException {
        List<UserProfile> users = store.loadUsers();
        Set<String> previouslyActive = store.loadActiveSlotIds();
        Set<Slot> currentlyAvailable = new HashSet<>();
        observedSlots.stream().filter(Slot::isBookable).forEach(currentlyAvailable::add);

        List<PendingPush> newNotifications = new ArrayList<>();
        for (Slot slot : currentlyAvailable) {
            if (previouslyActive.contains(slot.id())) continue;
            for (UserProfile user : users) {
                if (!user.matches(slot)) continue;
                for (PushSubscription subscription : user.subscriptions()) {
                    String id = notificationId(slot, user, subscription);
                    newNotifications.add(new PendingPush(id, user.uid(), subscription, slot));
                }
            }
        }

        store.updateAvailability(currentlyAvailable, previouslyActive, newNotifications);
        int sent = 0;
        int expired = 0;
        int retrying = 0;
        Set<String> expiredSubscriptions = new HashSet<>();
        Set<String> currentIds = currentlyAvailable.stream().map(Slot::id).collect(java.util.stream.Collectors.toSet());
        for (PendingPush push : store.loadPendingPushes()) {
            String subscriptionKey = push.uid() + "|" + push.subscription().id();
            if (expiredSubscriptions.contains(subscriptionKey)) continue;
            if (!currentIds.contains(push.slot().id())) {
                store.removePendingPush(push.id());
                continue;
            }
            PushGateway.DeliveryResult result = pushGateway.send(push.subscription(), push.slot(), push.id());
            switch (result) {
                case SENT -> {
                    store.removePendingPush(push.id());
                    sent++;
                }
                case EXPIRED -> {
                    expiredSubscriptions.add(subscriptionKey);
                    store.expireSubscription(push.uid(), push.subscription().id(), push.id());
                    expired++;
                }
                case RETRY -> retrying++;
            }
        }
        return new RunSummary(currentlyAvailable.size(), newNotifications.size(), sent, expired, retrying);
    }

    static String notificationId(Slot slot, UserProfile user, PushSubscription subscription) {
        try {
            byte[] bytes = java.security.MessageDigest.getInstance("SHA-256").digest(
                    (slot.id() + "|" + user.uid() + "|" + subscription.id())
                            .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        } catch (java.security.NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }

    public record RunSummary(int availableSlots, int newNotifications, int sent, int expiredSubscriptions, int retrying) {}
}
