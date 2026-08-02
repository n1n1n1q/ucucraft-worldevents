package net.ucucraft.worldevents.schedule;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.random.RandomGenerator;

import net.ucucraft.worldevents.util.Durations;

public record RandomSchedule(Duration min, Duration max, RandomGenerator random) implements EventSchedule {

    public RandomSchedule {
        if (min.isNegative() || max.compareTo(min) < 0) {
            throw new IllegalArgumentException("min-delay must be non-negative and not greater than max-delay");
        }
    }

    @Override
    public Optional<Instant> nextRun(Instant after) {
        long span = max.minus(min).toSeconds();
        long extra = span == 0 ? 0 : random.nextLong(span + 1);
        return Optional.of(after.plus(min).plusSeconds(extra));
    }

    @Override
    public String describe() {
        return "random every " + Durations.format(min) + " - " + Durations.format(max);
    }
}
