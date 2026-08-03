package net.ucucraft.worldevents.event;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.ucucraft.worldevents.lang.Msg;
import net.ucucraft.worldevents.util.Durations;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;

/**
 * Base class for every global world event.
 * <p>
 * Subclasses implement {@link #onStart} and {@link #onStop} and may override {@link #onTick}.
 * Lifecycle guarding, duration tracking and broadcasting are handled here, so
 * {@link #start} and {@link #stop} are safe to call from anywhere and are idempotent.
 */
public abstract class WorldEvent {

    private final String id;
    private final WorldEventContext context;
    private ConfigurationSection config;

    private EventState state = EventState.IDLE;
    private Instant startedAt;
    private Duration activeDuration = Duration.ZERO;

    protected WorldEvent(String id, WorldEventContext context, ConfigurationSection config) {
        this.id = id;
        this.context = context;
        this.config = config;
    }

    protected abstract void onStart(EventTrigger trigger);

    protected abstract void onStop(StopReason reason);

    protected void onTick() {
    }

    /** Override to refuse a start (missing dependency, still cleaning up a previous run, ...). */
    protected boolean canStart(EventTrigger trigger) {
        return true;
    }

    public final boolean start(EventTrigger trigger) {
        return start(trigger, null);
    }

    /** @param durationOverride runtime length for this run only, or {@code null} to use the config value */
    public final boolean start(EventTrigger trigger, Duration durationOverride) {
        if (state == EventState.RUNNING) {
            return false;
        }
        if (!canStart(trigger)) {
            return false;
        }
        state = EventState.RUNNING;
        startedAt = Instant.now();
        activeDuration = durationOverride != null ? durationOverride : duration();

        try {
            onStart(trigger);
        } catch (RuntimeException e) {
            state = EventState.IDLE;
            startedAt = null;
            throw e;
        }

        if (announce()) {
            context.lang().broadcast(Msg.EVENT_STARTED, placeholders());
        }
        return true;
    }

    public final boolean stop(StopReason reason) {
        if (state != EventState.RUNNING) {
            return false;
        }
        state = EventState.IDLE;
        startedAt = null;

        try {
            onStop(reason);
        } finally {
            if (announce() && reason.announced()) {
                context.lang().broadcast(Msg.EVENT_STOPPED, placeholders());
            }
        }
        return true;
    }

    final void tick() {
        if (state == EventState.RUNNING) {
            onTick();
        }
    }

    final void applyConfig(ConfigurationSection config) {
        this.config = config;
    }

    public final String id() {
        return id;
    }

    public final EventState state() {
        return state;
    }

    public final boolean running() {
        return state == EventState.RUNNING;
    }

    public final Optional<Instant> startedAt() {
        return Optional.ofNullable(startedAt);
    }

    /** Empty when the event runs until stopped manually. */
    public final Optional<Instant> endsAt() {
        if (startedAt == null || activeDuration.isZero()) {
            return Optional.empty();
        }
        return Optional.of(startedAt.plus(activeDuration));
    }

    protected final WorldEventContext context() {
        return context;
    }

    protected final ConfigurationSection config() {
        return config;
    }

    /** Event specific options; never {@code null}. */
    protected final ConfigurationSection settings() {
        ConfigurationSection settings = config.getConfigurationSection("settings");
        return settings != null ? settings : new MemoryConfiguration();
    }

    public boolean enabled() {
        return config.getBoolean("enabled", true);
    }

    public boolean announce() {
        return config.getBoolean("announce", true);
    }

    public String displayName() {
        return config.getString("display-name", id);
    }

    /** {@link Duration#ZERO} means the event runs until stopped manually. */
    public Duration duration() {
        return Durations.parseOr(config.getString("duration", "0s"), Duration.ZERO);
    }

    public final TagResolver[] placeholders() {
        return new TagResolver[]{
                Placeholder.unparsed("event", displayName()),
                Placeholder.unparsed("id", id)
        };
    }
}
