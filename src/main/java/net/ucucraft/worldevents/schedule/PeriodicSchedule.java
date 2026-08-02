package net.ucucraft.worldevents.schedule;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import net.ucucraft.worldevents.util.Durations;

public record PeriodicSchedule(Duration interval) implements EventSchedule {

    public PeriodicSchedule {
        if (interval.isZero() || interval.isNegative()) {
            throw new IllegalArgumentException("interval must be positive");
        }
    }

    @Override
    public Optional<Instant> nextRun(Instant after) {
        return Optional.of(after.plus(interval));
    }

    @Override
    public String describe() {
        return "every " + Durations.format(interval);
    }
}
