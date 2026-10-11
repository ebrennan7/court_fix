package uk.courtfix.checker;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;

public record WeeklyWindow(DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime) {
    public WeeklyWindow {
        if (dayOfWeek == null || startTime == null || endTime == null || !startTime.isBefore(endTime)) {
            throw new IllegalArgumentException("A weekly window requires a day and an increasing start/end time.");
        }
    }

    public static WeeklyWindow fromMap(Map<?, ?> value) {
        return new WeeklyWindow(
                DayOfWeek.valueOf(String.valueOf(value.get("dayOfWeek")).toUpperCase()),
                LocalTime.parse(String.valueOf(value.get("startTime"))),
                LocalTime.parse(String.valueOf(value.get("endTime"))));
    }

    public boolean matches(Slot slot) {
        LocalDate date = LocalDate.parse(slot.date());
        LocalTime start = LocalTime.parse(slot.startsAt());
        return date.getDayOfWeek() == dayOfWeek && !start.isBefore(startTime) && start.isBefore(endTime);
    }
}
