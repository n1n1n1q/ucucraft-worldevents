package net.ucucraft.worldevents;

import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
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
import net.ucucraft.worldevents.events.endstone.BlightService;
import net.ucucraft.worldevents.events.endstone.BlightSettings;
import net.ucucraft.worldevents.events.endstone.EndstoneBlightEvent;
import net.ucucraft.worldevents.lang.LangManager;
import net.ucucraft.worldevents.schedule.EventSchedule;
import net.ucucraft.worldevents.schedule.EventScheduler;
import net.ucucraft.worldevents.schedule.ScheduleFactory;
import org.bukkit.configuration.ConfigurationSection;
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
    private BlightService blightService;

    @Override
    public void onEnable() {
        configManager = new ConfigManager(this);
        configManager.reload();

        lang = new LangManager(this);
        lang.reload(configManager.language(), configManager.prefixEnabled());

        blightService = new BlightService(this, lang, random);

        registerEventTypes();
        load();

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> event.registrar()
                .register(WorldEventsCommand.build(this), "UCUCraft world events", List.of("we", "wevent")));

        getServer().getScheduler().runTaskLater(this, () -> blightService.recover(blightBackupDirectories()), 1L);
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
        if (blightService != null) {
            blightService.drainOnDisable(20_000L);
        }
    }

    /** Built-in event types are registered here; third parties can use {@link #registry()}. */
    private void registerEventTypes() {
        registry.register("endstone-blight", (id, context, config) ->
                new EndstoneBlightEvent(id, context, config, blightService));
    }

    /** Every {@code endstone-blight} event's configured backup directory, so recovery on startup
     *  checks all of them even if an event was later removed or renamed. */
    private Set<String> blightBackupDirectories() {
        Set<String> directories = new HashSet<>();
        for (String id : configManager.eventIds()) {
            ConfigurationSection section = configManager.event(id);
            if (section == null) {
                continue;
            }
            String type = section.getString("type", id).toLowerCase(Locale.ROOT);
            if (!type.equals("endstone-blight")) {
                continue;
            }
            BlightSettings settings = BlightSettings.parse(section.getConfigurationSection("settings"), getLogger());
            directories.add(settings.backup().directory());
        }
        if (directories.isEmpty()) {
            directories.add("blight");
        }
        return directories;
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
