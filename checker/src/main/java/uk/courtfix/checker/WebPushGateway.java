package uk.courtfix.checker;

import com.fasterxml.jackson.databind.ObjectMapper;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import org.apache.http.HttpResponse;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.security.Security;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class WebPushGateway implements PushGateway {
    private static final String BOOKING_URL = "https://bookings.better.org.uk/location/islington-tennis-centre/highbury-tennis";
    private final PushService service;
    private final ObjectMapper mapper = new ObjectMapper();

    public WebPushGateway(String publicKey, String privateKey) throws Exception {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
        service = new PushService(publicKey, privateKey, "https://ebrennan7.github.io/court_fix/");
    }

    @Override
    public DeliveryResult send(PushSubscription subscription, Slot slot, String notificationId) {
        try {
            Notification notification = new Notification(subscription.endpoint(), subscription.p256dh(),
                    subscription.auth(), mapper.writeValueAsString(payloadFor(slot, notificationId)));
            HttpResponse response = service.send(notification);
            int status = response.getStatusLine().getStatusCode();
            if (status >= 200 && status < 300) return DeliveryResult.SENT;
            if (status == 404 || status == 410) return DeliveryResult.EXPIRED;
            System.err.printf("Push provider returned HTTP %d.%n", status);
            return DeliveryResult.RETRY;
        } catch (Exception error) {
            System.err.printf("Push delivery failed (%s). Will retry on the next run.%n", error.getClass().getSimpleName());
            return DeliveryResult.RETRY;
        }
    }

    static Map<String, Object> payloadFor(Slot slot, String notificationId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", "A Highbury Fields court is available");
        payload.put("body", String.format("%s on %s, %s–%s is bookable now.", slot.courtName(),
                LocalDate.parse(slot.date()).format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.UK)), slot.startsAt(), slot.endsAt()));
        payload.put("url", BOOKING_URL);
        payload.put("tag", notificationId);
        return Map.copyOf(payload);
    }
}
