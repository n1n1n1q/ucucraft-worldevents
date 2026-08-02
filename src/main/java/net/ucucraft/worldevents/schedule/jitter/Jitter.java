package net.ucucraft.worldevents.schedule.jitter;

import java.time.Duration;
import java.util.random.RandomGenerator;

/**
 * A random offset applied to a scheduled run time. Negative shifts the run earlier.
 */
public interface Jitter {

    Jitter NONE = new Jitter() {
        @Override
        public Duration sample(RandomGenerator random) {
            return Duration.ZERO;
        }

        @Override
        public String describe() {
            return "none";
        }
    };

    Duration sample(RandomGenerator random);

    String describe();
}
