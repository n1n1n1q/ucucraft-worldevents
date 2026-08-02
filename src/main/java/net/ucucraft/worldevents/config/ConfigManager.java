package net.ucucraft.worldevents.config;

import java.time.ZoneId;
import java.util.Set;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class ConfigManager {

    private final JavaPlugin plugin;
    private FileConfiguration config;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        config = plugin.getConfig();
    }

    public void save() {
        plugin.saveConfig();
    }

    public FileConfiguration raw() {
        return config;
    }

    public ConfigurationSection events() {
        ConfigurationSection section = config.getConfigurationSection("events");
        return section != null ? section : config.createSection("events");
    }

    public Set<String> eventIds() {
        return events().getKeys(false);
    }

    public ConfigurationSection event(String id) {
        return events().getConfigurationSection(id);
    }

    public ConfigurationSection schedule(String id) {
        ConfigurationSection event = event(id);
        return event == null ? null : event.getConfigurationSection("schedule");
    }

    /** Schedule section for {@code id}, created if absent. Returns {@code null} for unknown events. */
    public ConfigurationSection orCreateSchedule(String id) {
        ConfigurationSection event = event(id);
        if (event == null) {
            return null;
        }
        ConfigurationSection schedule = event.getConfigurationSection("schedule");
        return schedule != null ? schedule : event.createSection("schedule");
    }

    public int tickInterval() {
        return Math.max(1, config.getInt("core.tick-interval", 20));
    }

    public ZoneId zone() {
        String raw = config.getString("core.timezone", "system");
        if (raw == null || raw.isBlank() || raw.equalsIgnoreCase("system")) {
            return ZoneId.systemDefault();
        }
        try {
            return ZoneId.of(raw.trim());
        } catch (RuntimeException e) {
            plugin.getLogger().warning("Unknown core.timezone '" + raw + "', using system default.");
            return ZoneId.systemDefault();
        }
    }

    public String language() {
        return config.getString("messages.language", "en");
    }

    public boolean prefixEnabled() {
        return config.getBoolean("messages.prefix-enabled", true);
    }
}
