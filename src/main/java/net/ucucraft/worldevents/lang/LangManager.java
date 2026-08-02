package net.ucucraft.worldevents.lang;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Loads {@code lang/<language>.yml} and renders messages through MiniMessage.
 * Missing keys fall back to the bundled English file.
 */
public final class LangManager {

    private static final String DEFAULT_LANGUAGE = "en";
    private static final List<String> BUNDLED = List.of("en", "uk");

    private final JavaPlugin plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    private YamlConfiguration messages = new YamlConfiguration();
    private YamlConfiguration defaults = new YamlConfiguration();
    private Component prefix = Component.empty();
    private boolean prefixEnabled = true;

    public LangManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload(String language, boolean prefixEnabled) {
        saveDefaults();
        this.prefixEnabled = prefixEnabled;
        this.defaults = bundled(DEFAULT_LANGUAGE);

        File file = new File(plugin.getDataFolder(), "lang/" + language + ".yml");
        if (!file.isFile()) {
            plugin.getLogger().warning("Language file 'lang/" + language + ".yml' not found, using "
                    + DEFAULT_LANGUAGE + ".");
            file = new File(plugin.getDataFolder(), "lang/" + DEFAULT_LANGUAGE + ".yml");
        }
        this.messages = YamlConfiguration.loadConfiguration(file);
        this.prefix = miniMessage.deserialize(raw(Msg.PREFIX));
    }

    public String raw(Msg msg) {
        return messages.getString(msg.path(), defaults.getString(msg.path(), msg.path()));
    }

    public Component render(Msg msg, TagResolver... resolvers) {
        return miniMessage.deserialize(raw(msg), resolvers);
    }

    public Component prefixed(Msg msg, TagResolver... resolvers) {
        Component message = render(msg, resolvers);
        return prefixEnabled ? prefix.append(message) : message;
    }

    public void send(Audience audience, Msg msg, TagResolver... resolvers) {
        audience.sendMessage(prefixed(msg, resolvers));
    }

    /** Sends without the prefix, for continuation lines of multi-line output. */
    public void sendRaw(Audience audience, Msg msg, TagResolver... resolvers) {
        audience.sendMessage(render(msg, resolvers));
    }

    public void broadcast(Msg msg, TagResolver... resolvers) {
        Bukkit.getServer().sendMessage(prefixed(msg, resolvers));
    }

    private void saveDefaults() {
        for (String name : BUNDLED) {
            String path = "lang/" + name + ".yml";
            if (!new File(plugin.getDataFolder(), path).isFile()) {
                plugin.saveResource(path, false);
            }
        }
    }

    private YamlConfiguration bundled(String language) {
        try (InputStream stream = plugin.getResource("lang/" + language + ".yml")) {
            if (stream == null) {
                return new YamlConfiguration();
            }
            return YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
        } catch (Exception e) {
            return new YamlConfiguration();
        }
    }
}
