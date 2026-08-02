package net.ucucraft.worldevents.event;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;

import net.ucucraft.worldevents.config.ConfigManager;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Owns the live event instances: builds them from the registry plus config, drives their ticks
 * and stops them when their duration elapses.
 */
public final class WorldEventManager {

    private final WorldEventRegistry registry;
    private final ConfigManager config;
    private final WorldEventContext context;
    private final Map<String, WorldEvent> events = new LinkedHashMap<>();

    public WorldEventManager(WorldEventRegistry registry, ConfigManager config, WorldEventContext context) {
        this.registry = registry;
        this.config = config;
        this.context = context;
    }

    public void load() {
        events.clear();
        for (String id : config.eventIds()) {
            ConfigurationSection section = config.event(id);
            if (section == null || !section.getBoolean("enabled", true)) {
                continue;
            }
            String type = section.getString("type", id).toLowerCase(Locale.ROOT);
            Optional<WorldEventFactory> factory = registry.factory(type);
            if (factory.isEmpty()) {
                context.plugin().getLogger().warning(
                        "No event type '" + type + "' registered for '" + id + "', skipping.");
                continue;
            }
            try {
                events.put(id, factory.get().create(id, context, section));
            } catch (RuntimeException e) {
                context.plugin().getLogger().log(Level.WARNING, "Failed to load event '" + id + "'", e);
            }
        }
    }

    public void unload(StopReason reason) {
        List.copyOf(events.values()).forEach(event -> event.stop(reason));
        events.clear();
    }

    public Optional<WorldEvent> event(String id) {
        return Optional.ofNullable(events.get(id));
    }

    public Collection<WorldEvent> events() {
        return Collections.unmodifiableCollection(events.values());
    }

    public Set<String> ids() {
        return Collections.unmodifiableSet(events.keySet());
    }

    public boolean start(String id, EventTrigger trigger) {
        return start(id, trigger, null);
    }

    public boolean start(String id, EventTrigger trigger, Duration durationOverride) {
        return event(id).map(event -> {
            try {
                return event.start(trigger, durationOverride);
            } catch (RuntimeException e) {
                context.plugin().getLogger().log(Level.SEVERE, "Event '" + id + "' failed to start", e);
                return false;
            }
        }).orElse(false);
    }

    public boolean stop(String id, StopReason reason) {
        return event(id).map(event -> event.stop(reason)).orElse(false);
    }

    public void tick() {
        Instant now = Instant.now();
        for (WorldEvent event : List.copyOf(events.values())) {
            if (!event.running()) {
                continue;
            }
            if (event.endsAt().filter(end -> !end.isAfter(now)).isPresent()) {
                event.stop(StopReason.FINISHED);
            } else {
                event.tick();
            }
        }
    }
}
