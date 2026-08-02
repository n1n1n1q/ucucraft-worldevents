package net.ucucraft.worldevents.event;

import java.time.ZoneId;
import java.util.random.RandomGenerator;

import net.ucucraft.worldevents.lang.LangManager;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Shared services handed to every event, so subclasses need a single constructor parameter.
 */
public record WorldEventContext(JavaPlugin plugin, LangManager lang, RandomGenerator random, ZoneId zone) {
}
