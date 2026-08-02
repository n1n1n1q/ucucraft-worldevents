package net.ucucraft.worldevents;

import java.time.ZoneId;
import java.util.List;
import java.util.logging.Level;
import java.util.random.RandomGenerator;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.ucucraft.worldevents.command.WorldEventsCommand;
import net.ucucraft.worldevents.config.ConfigManager;
import net.ucucraft.worldevents.event.StopReason;
import net.ucucraft.worldevents.event.WorldEvent;
import net.ucucraft.worldevents.event.WorldEventContext;
import net.ucucraft.worldevents.event.WorldEventManager;
import net.ucucraft.worldevents.event.WorldEventRegistry;
import net.ucucraft.worldevents.lang.LangManager;
import net.ucucraft.worldevents.schedule.EventSchedule;
import net.ucucraft.worldevents.schedule.EventScheduler;
import net.ucucraft.worldevents.schedule.ScheduleFactory;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

@SuppressWarnings("UnstableApiUsage")
public final class WorldEventsPlugin extends JavaPlugin {

    private final WorldEventRegistry registry = new WorldEventRegistry();
    private final RandomGenerator random = RandomGenerator.getDefault();
    private final ScheduleFactory scheduleFactory = new ScheduleFactory(random);

    private ConfigManager configManager;
    private LangManager lang;
    private WorldEventManager manager;
    private EventScheduler scheduler;
    private BukkitTask task;

    @Override
    public void onEnable() {
        configManager = new ConfigManager(this);
        configManager.reload();

        lang = new LangManager(this);
        lang.reload(configManager.language(), configManager.prefixEnabled());

        registerEventTypes();
        load();

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> event.registrar()
                .register(WorldEventsCommand.build(this), "UCUCraft world events", List.of("we", "wevent")));
    }

    @Override
    public void onDisable() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        if (manager != null) {
            manager.unload(StopReason.SHUTDOWN);
        }
    }

    /** Built-in event types are registered here; third parties can use {@link #registry()}. */
    private void registerEventTypes() {
        // registry.register("chunk-swap", ChunkSwapEvent::new);
    }

    public void reload() {
        if (manager != null) {
            manager.unload(StopReason.RELOAD);
        }
        configManager.reload();
        lang.reload(configManager.language(), configManager.prefixEnabled());
        load();
    }

    private void load() {
        ZoneId zone = configManager.zone();
        manager = new WorldEventManager(registry, configManager, new WorldEventContext(this, lang, random, zone));
        scheduler = new EventScheduler(manager);
        manager.load();

        for (WorldEvent event : manager.events()) {
            scheduler.install(event.id(), buildSchedule(event.id()));
        }
        restartTask();
    }

    /** Rebuilds an event's schedule from config and re-arms it. */
    public void installSchedule(String id) {
        scheduler.install(id, buildSchedule(id));
    }

    private EventSchedule buildSchedule(String id) {
        try {
            return scheduleFactory.create(configManager.schedule(id), configManager.zone());
        } catch (RuntimeException e) {
            getLogger().log(Level.WARNING, "Invalid schedule for event '" + id + "', falling back to manual", e);
            return EventSchedule.NONE;
        }
    }

    private void restartTask() {
        if (task != null) {
            task.cancel();
        }
        int interval = configManager.tickInterval();
        task = getServer().getScheduler().runTaskTimer(this, () -> {
            scheduler.tick();
            manager.tick();
        }, interval, interval);
    }

    public WorldEventRegistry registry() {
        return registry;
    }

    public WorldEventManager manager() {
        return manager;
    }

    public EventScheduler scheduler() {
        return scheduler;
    }

    public ConfigManager configManager() {
        return configManager;
    }

    public LangManager lang() {
        return lang;
    }
}
