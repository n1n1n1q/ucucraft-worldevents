package net.ucucraft.worldevents.schedule.jitter;

import java.time.Duration;

public enum DistributionType {

    NONE {
        @Override
        public Jitter create(Duration before, Duration after) {
            return Jitter.NONE;
        }
    },
    UNIFORM {
        @Override
        public Jitter create(Duration before, Duration after) {
            return new UniformJitter(before, after);
        }
    },
    NORMAL {
        @Override
        public Jitter create(Duration before, Duration after) {
            return new GaussianJitter(before, after);
        }
    };

    public abstract Jitter create(Duration before, Duration after);
}
