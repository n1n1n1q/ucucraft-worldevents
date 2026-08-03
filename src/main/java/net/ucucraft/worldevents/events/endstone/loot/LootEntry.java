package net.ucucraft.worldevents.events.endstone.loot;

import org.bukkit.Material;

/**
 * One layer of a stacked entry: the base definition, a tool-specific delta, or an enchantment-specific
 * delta. {@code material} is {@code null} when this layer only deepens chance/amount of an entry whose
 * material another layer already defines.
 */
public record LootEntry(Material material, double chance, int minAmount, int maxAmount) {
}
