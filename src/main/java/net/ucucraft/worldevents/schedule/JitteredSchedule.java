package net.ucucraft.worldevents.schedule;

import java.time.Instant;
import java.util.Optional;
import java.util.random.RandomGenerator;

import net.ucucraft.worldevents.schedule.jitter.Jitter;

/**
 * Shifts any other schedule by a random offset, so "every 6 hours" can become
 * "every 6 hours, give or take 20 minutes". Never moves a run into the past.
 */
public record JitteredSchedule(EventSchedule delegate, Jitter jitter, RandomGenerator random) implements EventSchedule {

    @Override
    public Optional<Instant> nextRun(Instant after) {
        return delegate.nextRun(after).map(base -> {
            Instant shifted = base.plus(jitter.sample(random));
            return shifted.isBefore(after) ? after : shifted;
        });
    }

    @Override
    public String describe() {
        return delegate.describe() + ", jitter " + jitter.describe();
    }
}
