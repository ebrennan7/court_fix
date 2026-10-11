package uk.courtfix.checker;

public record Slot(
        String id,
        String courtId,
        String courtName,
        String date,
        String startsAt,
        String endsAt,
        String status,
        int spaces,
        String price) {

    public boolean isBookable() {
        return "BOOK".equalsIgnoreCase(status) && spaces > 0;
    }
}
