package uk.courtfix.checker;

public record PendingPush(String id, String uid, PushSubscription subscription, Slot slot) {}
