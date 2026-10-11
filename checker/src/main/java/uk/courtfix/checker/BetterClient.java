package uk.courtfix.checker;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public final class BetterClient {
    private static final String ENDPOINT_PREFIX =
            "https://better-admin.org.uk/api/activities/venue/islington-tennis-centre/"
                    + "activity/highbury-tennis/v2/slots?date=";
    private static final String REFERER =
            "https://bookings.better.org.uk/location/islington-tennis-centre/highbury-tennis";

    private final HttpClient http;
    private final String endpointPrefix;
    public BetterClient() {
        this(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build(), ENDPOINT_PREFIX);
    }

    BetterClient(HttpClient http, String endpointPrefix) {
        this.http = http;
        this.endpointPrefix = endpointPrefix;
    }

    public List<Slot> getSlots(LocalDate date) throws IOException, InterruptedException {
        URI uri = URI.create(endpointPrefix + date);
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json, text/plain, */*")
                .header("Origin", "https://bookings.better.org.uk")
                .header("Referer", REFERER)
                .header("X-Requested-With", "XMLHttpRequest")
                .header("User-Agent", "CourtFix/0.1 (personal availability checker)")
                .GET()
                .build();

        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 422) {
            throw new IOException("Better rejected date " + date
                    + " as outside the currently viewable booking window (HTTP 422).");
        }
        if (response.statusCode() != 200) {
            throw new IOException("Better availability request failed with HTTP " + response.statusCode());
        }

        return parseSlots(response.body(), date);
    }

    static List<Slot> parseSlots(String body, LocalDate date) throws IOException {
        JsonNode data = new ObjectMapper().readTree(body).path("data");
        if (!data.isArray()) {
            throw new IOException("Better response did not contain a data array.");
        }

        List<Slot> slots = new ArrayList<>();
        for (JsonNode item : data) {
            JsonNode location = item.path("location");
            JsonNode action = item.path("action_to_show");
            slots.add(new Slot(
                    item.path("id").asText(""),
                    location.path("id").asText(""),
                    location.path("name").asText("Unknown court"),
                    item.path("date").path("raw").asText(date.toString()),
                    item.path("starts_at").path("format_24_hour").asText(""),
                    item.path("ends_at").path("format_24_hour").asText(""),
                    action.path("status").asText("UNKNOWN"),
                    item.path("spaces").asInt(0),
                    item.path("price").path("formatted").asText("")));
        }
        return slots;
    }
}
