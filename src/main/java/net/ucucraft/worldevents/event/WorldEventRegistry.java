package net.ucucraft.worldevents.event;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Extension point: every event type registers its factory here before the manager loads.
 */
public final class WorldEventRegistry {

    private final Map<String, WorldEventFactory> factories = new LinkedHashMap<>();

    public void register(String id, WorldEventFactory factory) {
        String key = id.toLowerCase(Locale.ROOT);
        if (factories.putIfAbsent(key, factory) != null) {
            throw new IllegalStateException("event type already registered: " + key);
        }
    }

    public Optional<WorldEventFactory> factory(String id) {
        return Optional.ofNullable(factories.get(id.toLowerCase(Locale.ROOT)));
    }

    public Set<String> ids() {
        return Collections.unmodifiableSet(factories.keySet());
    }
}
