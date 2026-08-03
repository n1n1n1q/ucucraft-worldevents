package net.ucucraft.worldevents.events.endstone.loot;

import java.util.Map;
import java.util.Set;

/**
 * An enchantment's effect on the table: a multiplier applied to every entry it covers
 * ({@code appliesTo} empty means all), plus optional additive entries of its own (e.g. Fortune
 * adding a redstone entry that wouldn't otherwise exist).
 */
public record LootModifier(String enchantId, double perLevel, Set<String> appliesTo, Map<String, LootEntry> entries) {
}
