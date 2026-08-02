package net.ucucraft.worldevents.schedule.jitter;

import java.time.Duration;
import java.util.random.RandomGenerator;

import net.ucucraft.worldevents.util.Durations;

/** Every offset in {@code [-before, +after]} is equally likely. */
public record UniformJitter(Duration before, Duration after) implements Jitter {

    public UniformJitter {
        if (before.isNegative() || after.isNegative()) {
            throw new IllegalArgumentException("jitter bounds must be non-negative");
        }
    }

    @Override
    public Duration sample(RandomGenerator random) {
        long span = before.toSeconds() + after.toSeconds();
        if (span <= 0) {
            return Duration.ZERO;
        }
        return Duration.ofSeconds(random.nextLong(span + 1) - before.toSeconds());
    }

    @Override
    public String describe() {
        return "uniform -" + Durations.format(before) + " / +" + Durations.format(after);
    }
}
