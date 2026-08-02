package net.ucucraft.worldevents.schedule.jitter;

import java.time.Duration;
import java.util.random.RandomGenerator;

import net.ucucraft.worldevents.util.Durations;

/**
 * Normally distributed offset centred on the scheduled time. The bounds are treated as three
 * standard deviations and are hard clamps, so a run never lands outside them.
 */
public record GaussianJitter(Duration before, Duration after) implements Jitter {

    public GaussianJitter {
        if (before.isNegative() || after.isNegative()) {
            throw new IllegalArgumentException("jitter bounds must be non-negative");
        }
    }

    @Override
    public Duration sample(RandomGenerator random) {
        double normalised = Math.clamp(random.nextGaussian() / 3.0, -1.0, 1.0);
        double seconds = normalised * (normalised < 0 ? before.toSeconds() : after.toSeconds());
        return Duration.ofSeconds(Math.round(seconds));
    }

    @Override
    public String describe() {
        return "normal -" + Durations.format(before) + " / +" + Durations.format(after);
    }
}
