package uk.courtfix.checker;

public record PushSubscription(String id, String endpoint, String p256dh, String auth) {}
