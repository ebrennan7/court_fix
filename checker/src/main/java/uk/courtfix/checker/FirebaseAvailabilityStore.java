package uk.courtfix.checker;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.cloud.firestore.WriteBatch;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public final class FirebaseAvailabilityStore implements AvailabilityStore {
    private static final String USERS = "users";
    private static final String AVAILABILITY = "availabilityState";
    private static final String OUTBOX = "notificationOutbox";
    private static final ZoneId LONDON = ZoneId.of("Europe/London");
    private final Firestore firestore;

    public FirebaseAvailabilityStore(Firestore firestore) {
        this.firestore = firestore;
    }

    @Override
    public List<UserProfile> loadUsers() throws IOException, InterruptedException {
        List<UserProfile> users = new ArrayList<>();
        for (QueryDocumentSnapshot preferences : get(firestore.collectionGroup("preferences").get()).getDocuments()) {
            if (!"main".equals(preferences.getId())) continue;
            String uid = preferences.getReference().getParent().getParent().getId();
            Map<String, Object> data = preferences.getData();
            Set<String> courtIds = strings(data.get("courtIds"));
            List<WeeklyWindow> windows = windows(data.get("windows"));
            List<PushSubscription> subscriptions = new ArrayList<>();
            for (QueryDocumentSnapshot subscription : get(firestore.collection(USERS).document(uid)
                    .collection("subscriptions").get()).getDocuments()) {
                Map<String, Object> subscriptionData = subscription.getData();
                if (!Boolean.TRUE.equals(subscriptionData.get("active"))) continue;
                Object keysValue = subscriptionData.get("keys");
                if (!(keysValue instanceof Map<?, ?> keys)) continue;
                String endpoint = string(subscriptionData.get("endpoint"));
                String p256dh = string(keys.get("p256dh"));
                String auth = string(keys.get("auth"));
                if (!endpoint.isBlank() && !p256dh.isBlank() && !auth.isBlank()) {
                    subscriptions.add(new PushSubscription(subscription.getId(), endpoint, p256dh, auth));
                }
            }
            if (!courtIds.isEmpty() && !windows.isEmpty() && !subscriptions.isEmpty()) {
                users.add(new UserProfile(uid, courtIds, windows, subscriptions));
            }
        }
        return users;
    }

    @Override
    public Set<String> loadActiveSlotIds() throws IOException, InterruptedException {
        Set<String> active = new HashSet<>();
        for (QueryDocumentSnapshot state : get(firestore.collection(AVAILABILITY).whereEqualTo("active", true).get()).getDocuments()) {
            active.add(string(state.get("slotId")));
        }
        active.remove("");
        return active;
    }

    @Override
    public void updateAvailability(Set<Slot> currentSlots, Set<String> previousSlotIds,
                                   List<PendingPush> newNotifications) throws IOException, InterruptedException {
        Set<String> currentIds = currentSlots.stream().map(Slot::id).collect(java.util.stream.Collectors.toSet());
        Map<String, Slot> currentById = new HashMap<>();
        currentSlots.forEach(slot -> currentById.put(slot.id(), slot));
        Map<String, QueryDocumentSnapshot> stateBySlotId = new HashMap<>();
        for (QueryDocumentSnapshot state : get(firestore.collection(AVAILABILITY).get()).getDocuments()) {
            stateBySlotId.put(string(state.get("slotId")), state);
        }

        List<Consumer<WriteBatch>> notificationWrites = new ArrayList<>();
        List<Consumer<WriteBatch>> stateWrites = new ArrayList<>();
        for (String slotId : currentIds) {
            QueryDocumentSnapshot state = stateBySlotId.get(slotId);
            if (state != null && Boolean.TRUE.equals(state.getBoolean("active"))) continue;
            Slot slot = currentById.get(slotId);
            DocumentReference reference = firestore.collection(AVAILABILITY).document(hash(slotId));
            Map<String, Object> data = Map.of("slotId", slotId, "date", slot.date(), "active", true, "updatedAt", Instant.now().toString());
            stateWrites.add(batch -> batch.set(reference, data));
        }
        for (String slotId : previousSlotIds) {
            if (currentIds.contains(slotId)) continue;
            QueryDocumentSnapshot state = stateBySlotId.get(slotId);
            if (state == null || !Boolean.TRUE.equals(state.getBoolean("active"))) continue;
            DocumentReference reference = state.getReference();
            stateWrites.add(batch -> batch.update(reference, Map.of("active", false, "updatedAt", Instant.now().toString())));
        }
        LocalDate oldestRetainedDate = LocalDate.now(LONDON).minusDays(1);
        for (QueryDocumentSnapshot state : stateBySlotId.values()) {
            String date = string(state.get("date"));
            if (!date.isBlank() && LocalDate.parse(date).isBefore(oldestRetainedDate)) {
                DocumentReference reference = state.getReference();
                stateWrites.add(batch -> batch.delete(reference));
            }
        }
        for (PendingPush notification : newNotifications) {
            Slot slot = notification.slot();
            Map<String, Object> subscription = Map.of("id", notification.subscription().id(),
                    "endpoint", notification.subscription().endpoint(), "p256dh", notification.subscription().p256dh(),
                    "auth", notification.subscription().auth());
            Map<String, Object> slotData = Map.of("id", slot.id(), "courtId", slot.courtId(), "courtName", slot.courtName(),
                    "date", slot.date(), "startsAt", slot.startsAt(), "endsAt", slot.endsAt());
            DocumentReference reference = firestore.collection(OUTBOX).document(notification.id());
            Map<String, Object> data = Map.of("uid", notification.uid(), "subscription", subscription,
                    "subscriptionId", notification.subscription().id(), "slot", slotData,
                    "status", "PENDING", "createdAt", Instant.now().toString());
            notificationWrites.add(batch -> batch.set(reference, data));
        }
        List<Consumer<WriteBatch>> writes = new ArrayList<>(notificationWrites);
        writes.addAll(stateWrites);
        for (int start = 0; start < writes.size(); start += 400) {
            WriteBatch batch = firestore.batch();
            writes.subList(start, Math.min(start + 400, writes.size())).forEach(write -> write.accept(batch));
            get(batch.commit());
        }
    }

    @Override
    public List<PendingPush> loadPendingPushes() throws IOException, InterruptedException {
        List<PendingPush> pending = new ArrayList<>();
        for (QueryDocumentSnapshot doc : get(firestore.collection(OUTBOX).whereEqualTo("status", "PENDING").get()).getDocuments()) {
            Map<String, Object> data = doc.getData();
            Map<?, ?> subscription = (Map<?, ?>) data.get("subscription");
            Map<?, ?> slot = (Map<?, ?>) data.get("slot");
            PushSubscription pushSubscription = new PushSubscription(string(subscription.get("id")),
                    string(subscription.get("endpoint")), string(subscription.get("p256dh")), string(subscription.get("auth")));
            Slot availableSlot = new Slot(string(slot.get("id")), string(slot.get("courtId")), string(slot.get("courtName")),
                    string(slot.get("date")), string(slot.get("startsAt")), string(slot.get("endsAt")), "BOOK", 1, "");
            pending.add(new PendingPush(doc.getId(), string(data.get("uid")), pushSubscription, availableSlot));
        }
        return pending;
    }

    @Override
    public void removePendingPush(String id) throws IOException, InterruptedException {
        get(firestore.collection(OUTBOX).document(id).delete());
    }

    @Override
    public void expireSubscription(String uid, String subscriptionId, String pushId) throws IOException, InterruptedException {
        get(firestore.collection(USERS).document(uid).collection("subscriptions").document(subscriptionId).delete());
        QuerySnapshot related = get(firestore.collection(OUTBOX).whereEqualTo("subscriptionId", subscriptionId).get());
        List<Consumer<WriteBatch>> writes = new ArrayList<>();
        for (QueryDocumentSnapshot delivery : related.getDocuments()) {
            if (uid.equals(delivery.getString("uid")) && "PENDING".equals(delivery.getString("status"))) {
                DocumentReference reference = delivery.getReference();
                writes.add(batch -> batch.delete(reference));
            }
        }
        if (writes.isEmpty()) {
            get(firestore.collection(OUTBOX).document(pushId).delete());
            return;
        }
        for (int start = 0; start < writes.size(); start += 400) {
            WriteBatch batch = firestore.batch();
            writes.subList(start, Math.min(start + 400, writes.size())).forEach(write -> write.accept(batch));
            get(batch.commit());
        }
    }

    private static <T> T get(ApiFuture<T> future) throws IOException, InterruptedException {
        try {
            return future.get();
        } catch (java.util.concurrent.ExecutionException error) {
            throw new IOException("Firestore operation failed: " + error.getCause().getMessage(), error.getCause());
        }
    }

    static Set<String> strings(Object value) {
        if (!(value instanceof List<?> list)) return Set.of();
        return list.stream().map(FirebaseAvailabilityStore::string).filter(s -> !s.isBlank())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    static List<WeeklyWindow> windows(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        List<WeeklyWindow> result = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) continue;
            try {
                result.add(WeeklyWindow.fromMap(map));
            } catch (RuntimeException ignored) {
                // One malformed row should not prevent other users' valid preferences from being checked.
            }
        }
        return List.copyOf(result);
    }

    private static String string(Object value) { return value == null ? "" : String.valueOf(value); }

    private static String hash(String input) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256").digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (java.security.NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }
}
