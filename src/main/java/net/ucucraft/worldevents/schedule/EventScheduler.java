package net.ucucraft.worldevents.schedule;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.ucucraft.worldevents.event.EventTrigger;
import net.ucucraft.worldevents.event.WorldEventManager;

/**
 * Tracks the next run time of every event. Pending runs live in memory only and are recomputed
 * from scratch on startup.
 */
public final class EventScheduler {

    private final WorldEventManager manager;
    private final Map<String, EventSchedule> schedules = new LinkedHashMap<>();
    private final Map<String, Instant> pending = new LinkedHashMap<>();

    public EventScheduler(WorldEventManager manager) {
        this.manager = manager;
    }

    public void install(String id, EventSchedule schedule) {
        schedules.put(id, schedule);
        pending.remove(id);
        advance(id, Instant.now());
    }

    public void clear() {
        schedules.clear();
        pending.clear();
    }

    public EventSchedule schedule(String id) {
        return schedules.getOrDefault(id, EventSchedule.NONE);
    }

    public Optional<Instant> nextRun(String id) {
        return Optional.ofNullable(pending.get(id));
    }

    /** Overrides the next run time; the schedule resumes normally afterwards. */
    public void scheduleAt(String id, Instant at) {
        pending.put(id, at);
    }

    /** Skips the pending run and rolls forward to the one after it. */
    public boolean cancel(String id) {
        Instant cancelled = pending.remove(id);
        if (cancelled == null) {
            return false;
        }
        advance(id, cancelled);
        return true;
    }

    public void reschedule(String id) {
        pending.remove(id);
        advance(id, Instant.now());
    }

    public void tick() {
        Instant now = Instant.now();
        List<String> due = pending.entrySet().stream()
                .filter(entry -> !entry.getValue().isAfter(now))
                .map(Map.Entry::getKey)
                .toList();

        for (String id : due) {
            Instant planned = pending.remove(id);
            manager.start(id, EventTrigger.SCHEDULE);
            advance(id, planned);
        }
    }

    private void advance(String id, Instant from) {
        EventSchedule schedule = schedules.get(id);
        if (schedule == null) {
            return;
        }
        Instant now = Instant.now();
        Optional<Instant> next = schedule.nextRun(from);
        if (next.isPresent() && !next.get().isAfter(now)) {
            next = schedule.nextRun(now);
        }
        next.ifPresent(instant -> pending.put(id, instant));
    }
}
