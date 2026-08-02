package net.ucucraft.worldevents.schedule;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Fires at fixed wall-clock times of day. An empty {@code days} set means every day.
 */
public record FixedTimeSchedule(List<LocalTime> times, ZoneId zone, Set<DayOfWeek> days) implements EventSchedule {

    public FixedTimeSchedule {
        if (times.isEmpty()) {
            throw new IllegalArgumentException("at least one time is required");
        }
        times = times.stream().sorted().distinct().toList();
        days = days.isEmpty() ? EnumSet.allOf(DayOfWeek.class) : EnumSet.copyOf(days);
    }

    @Override
    public Optional<Instant> nextRun(Instant after) {
        LocalDate start = after.atZone(zone).toLocalDate();
        for (int offset = 0; offset <= 7; offset++) {
            LocalDate date = start.plusDays(offset);
            if (!days.contains(date.getDayOfWeek())) {
                continue;
            }
            for (LocalTime time : times) {
                Instant candidate = ZonedDateTime.of(date, time, zone).toInstant();
                if (candidate.isAfter(after)) {
                    return Optional.of(candidate);
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public String describe() {
        String at = times.stream().map(LocalTime::toString).collect(Collectors.joining(", "));
        String on = days.size() == 7 ? "daily" : days.stream()
                .sorted()
                .map(day -> day.name().substring(0, 3))
                .collect(Collectors.joining(", "));
        return "at " + at + " (" + on + ", " + zone.getId() + ")";
    }
}
