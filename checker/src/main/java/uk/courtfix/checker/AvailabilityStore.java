package uk.courtfix.checker;

import java.io.IOException;
import java.util.List;
import java.util.Set;

public interface AvailabilityStore {
    List<UserProfile> loadUsers() throws IOException, InterruptedException;
    Set<String> loadActiveSlotIds() throws IOException, InterruptedException;
    void updateAvailability(Set<Slot> currentSlots, Set<String> previousSlotIds,
                            List<PendingPush> newNotifications) throws IOException, InterruptedException;
    List<PendingPush> loadPendingPushes() throws IOException, InterruptedException;
    void removePendingPush(String id) throws IOException, InterruptedException;
    void expireSubscription(String uid, String subscriptionId, String pushId) throws IOException, InterruptedException;
}
