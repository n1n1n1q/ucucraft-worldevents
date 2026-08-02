package net.ucucraft.worldevents.event;

import org.bukkit.configuration.ConfigurationSection;

@FunctionalInterface
public interface WorldEventFactory {

    WorldEvent create(String id, WorldEventContext context, ConfigurationSection config);
}
