package uk.courtfix.checker;

import java.util.List;
import java.util.Set;

public record UserProfile(String uid, Set<String> courtIds, List<WeeklyWindow> windows,
                          List<PushSubscription> subscriptions) {
    public boolean matches(Slot slot) {
        return courtIds.contains(slot.courtId()) && windows.stream().anyMatch(window -> window.matches(slot));
    }
}
