package uk.courtfix.checker;

public interface PushGateway {
    DeliveryResult send(PushSubscription subscription, Slot slot, String notificationId);

    enum DeliveryResult { SENT, EXPIRED, RETRY }
}
