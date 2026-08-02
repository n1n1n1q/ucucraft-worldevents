package net.ucucraft.worldevents.schedule;

import java.time.Instant;
import java.util.Optional;

/**
 * Decides when an event runs next. Implementations are pure time math and hold no server state.
 */
public interface EventSchedule {

    /** A schedule that never fires on its own; the event can still be started manually. */
    EventSchedule NONE = new EventSchedule() {
        @Override
        public Optional<Instant> nextRun(Instant after) {
            return Optional.empty();
        }

        @Override
        public String describe() {
            return "manual";
        }
    };

    Optional<Instant> nextRun(Instant after);

    String describe();
}
