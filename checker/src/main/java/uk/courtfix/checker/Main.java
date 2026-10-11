package uk.courtfix.checker;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.cloud.FirestoreClient;
import com.google.cloud.firestore.Firestore;
import com.fasterxml.jackson.databind.JsonNode;

public final class Main {
    private static final ZoneId LONDON = ZoneId.of("Europe/London");

    private Main() {}

    public static void main(String[] args) throws Exception {
        LocalDate startDate = parseDate(args, LocalDate.now(LONDON), System.getenv("START_DATE"));
        int daysAhead = Integer.parseInt(System.getenv().getOrDefault("DAYS_AHEAD", "5"));
        List<Court> courts = loadCourts();
        Set<String> courtIds = courts.stream().map(Court::id).collect(Collectors.toSet());
        Map<String, String> courtNames = courts.stream()
                .collect(Collectors.toMap(Court::id, Court::name));
        BetterClient better = new BetterClient();
        List<Slot> observedSlots = new java.util.ArrayList<>();

        int checked = 0;
        int available = 0;
        System.out.printf("Court Fix check started at %s (%s through %s)%n",
                java.time.ZonedDateTime.now(LONDON), startDate, startDate.plusDays(daysAhead));

        for (int day = 0; day <= daysAhead; day++) {
            LocalDate date = startDate.plusDays(day);
            List<Slot> daySlots = better.getSlots(date).stream()
                    .filter(slot -> courtIds.contains(slot.courtId()))
                    .toList();
            checked += daySlots.size();
            List<Slot> bookable = daySlots.stream().filter(Slot::isBookable).toList();
            observedSlots.addAll(daySlots);
            available += bookable.size();
            System.out.printf("%s: %d slots, %d bookable%n", date, daySlots.size(), bookable.size());
            for (Slot slot : bookable) {
                System.out.printf("  %s | %s-%s | %s | %s spaces | %s%n",
                        courtNames.getOrDefault(slot.courtId(), slot.courtName()),
                        slot.startsAt(), slot.endsAt(), slot.date(), slot.spaces(), slot.price());
            }
            if (day < daysAhead) {
                Thread.sleep(1_000L);
            }
        }
        System.out.printf("Finished: %d slots checked; %d currently bookable.%n", checked, available);
        String serviceAccount = System.getenv("FIREBASE_SERVICE_ACCOUNT_JSON");
        String vapidPublicKey = System.getenv("VAPID_PUBLIC_KEY");
        String vapidPrivateKey = System.getenv("VAPID_PRIVATE_KEY");
        if (isBlank(serviceAccount) && isBlank(vapidPublicKey) && isBlank(vapidPrivateKey)) {
            System.out.println("Notification delivery is not configured yet; availability check only.");
            return;
        }
        if (isBlank(serviceAccount) || isBlank(vapidPublicKey) || isBlank(vapidPrivateKey)) {
            throw new IllegalStateException("Configure FIREBASE_SERVICE_ACCOUNT_JSON, VAPID_PUBLIC_KEY, and VAPID_PRIVATE_KEY together.");
        }
        vapidPublicKey = vapidPublicKey.strip();
        vapidPrivateKey = vapidPrivateKey.strip();
        JsonNode serviceAccountJson = new ObjectMapper().readTree(serviceAccount);
        String firebaseProjectId = serviceAccountJson.path("project_id").asText();
        if (firebaseProjectId.isBlank()) throw new IllegalStateException("Firebase service account JSON is missing project_id.");
        FirebaseOptions options = FirebaseOptions.builder()
                .setProjectId(firebaseProjectId)
                .setCredentials(GoogleCredentials.fromStream(new ByteArrayInputStream(serviceAccount.getBytes(StandardCharsets.UTF_8))))
                .build();
        FirebaseApp app = FirebaseApp.initializeApp(options, "court-fix-checker");
        try {
            Firestore firestore = FirestoreClient.getFirestore(app);
            try {
                AvailabilityProcessor processor = new AvailabilityProcessor(
                        new FirebaseAvailabilityStore(firestore), new WebPushGateway(vapidPublicKey, vapidPrivateKey));
                AvailabilityProcessor.RunSummary summary = processor.process(observedSlots);
                System.out.printf("Notifications: %d newly matched, %d sent, %d expired subscriptions, %d will retry.%n",
                        summary.newNotifications(), summary.sent(), summary.expiredSubscriptions(), summary.retrying());
            } finally {
                firestore.close();
            }
        } finally {
            app.delete();
        }
    }

    static LocalDate parseDate(String[] args, LocalDate defaultDate, String envDate) {
        for (String arg : args) {
            if (arg.startsWith("--date=")) {
                return LocalDate.parse(arg.substring("--date=".length()));
            }
        }
        return envDate == null || envDate.isBlank() ? defaultDate : LocalDate.parse(envDate);
    }

    static List<Court> loadCourts() throws Exception {
        Path config = Path.of("config", "courts.json");
        if (!Files.exists(config)) {
            config = Path.of("..", "config", "courts.json");
        }
        if (!Files.exists(config)) {
            throw new IllegalStateException("Cannot find config/courts.json from the repository or checker directory.");
        }
        return loadCourts(config);
    }

    static List<Court> loadCourts(Path config) throws Exception {
        return new ObjectMapper().readValue(Files.readString(config), new TypeReference<>() {});
    }

    private static boolean isBlank(String value) { return value == null || value.isBlank(); }
}
