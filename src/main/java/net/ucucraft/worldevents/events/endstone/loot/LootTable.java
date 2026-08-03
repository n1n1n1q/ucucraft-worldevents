package net.ucucraft.worldevents.events.endstone.loot;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.ToDoubleFunction;
import java.util.logging.Logger;
import java.util.random.RandomGenerator;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Additive entries plus multiplicative enchantment modifiers. Entries are keyed by id so a tool or
 * enchantment layer can deepen an entry another layer already defines, rather than duplicating it.
 * Pure arithmetic on primitives, deliberately kept server-independent so it is unit-testable: material
 * and enchantment identity are resolved to plain strings/enums once at parse time, never re-touched
 * by {@link #roll}.
 */
public final class LootTable {

    private final Map<String, LootEntry> base;
    private final Map<String, Map<String, LootEntry>> tools;
    private final List<LootModifier> modifiers;
    private final double chanceCap;

    private LootTable(Map<String, LootEntry> base, Map<String, Map<String, LootEntry>> tools,
                       List<LootModifier> modifiers, double chanceCap) {
        this.base = base;
        this.tools = tools;
        this.modifiers = modifiers;
        this.chanceCap = chanceCap;
    }

    public static LootTable parse(ConfigurationSection section, Logger logger) {
        Map<String, LootEntry> base = new LinkedHashMap<>();
        ConfigurationSection baseSection = section.getConfigurationSection("base");
        if (baseSection != null) {
            for (String id : baseSection.getKeys(false)) {
                ConfigurationSection entrySection = baseSection.getConfigurationSection(id);
                if (entrySection == null) {
                    continue;
                }
                LootEntry entry = parseEntry(entrySection, true, logger, "loot.base." + id);
                if (entry != null) {
                    base.put(id, entry);
                }
            }
        }

        Map<String, Map<String, LootEntry>> tools = new LinkedHashMap<>();
        ConfigurationSection toolsSection = section.getConfigurationSection("tools");
        if (toolsSection != null) {
            for (String toolKey : toolsSection.getKeys(false)) {
                ConfigurationSection toolSection = toolsSection.getConfigurationSection(toolKey);
                if (toolSection == null) {
                    continue;
                }
                Map<String, LootEntry> perId = new LinkedHashMap<>();
                for (String id : toolSection.getKeys(false)) {
                    ConfigurationSection entrySection = toolSection.getConfigurationSection(id);
                    if (entrySection == null) {
                        continue;
                    }
                    LootEntry entry = parseEntry(entrySection, false, logger,
                            "loot.tools." + toolKey + "." + id);
                    if (entry != null) {
                        perId.put(id, entry);
                    }
                }
                tools.put(toolKey.toUpperCase(Locale.ROOT), perId);
            }
        }

        List<LootModifier> modifiers = new ArrayList<>();
        ConfigurationSection enchantSection = section.getConfigurationSection("enchantments");
        if (enchantSection != null) {
            for (String enchantId : enchantSection.getKeys(false)) {
                ConfigurationSection modSection = enchantSection.getConfigurationSection(enchantId);
                if (modSection == null) {
                    continue;
                }
                double perLevel = modSection.getDouble("per-level", 0);
                Set<String> appliesTo = new LinkedHashSet<>(modSection.getStringList("applies-to"));
                Map<String, LootEntry> entries = new LinkedHashMap<>();
                ConfigurationSection entriesSection = modSection.getConfigurationSection("entries");
                if (entriesSection != null) {
                    for (String id : entriesSection.getKeys(false)) {
                        ConfigurationSection entrySection = entriesSection.getConfigurationSection(id);
                        if (entrySection == null) {
                            continue;
                        }
                        LootEntry entry = parseEntry(entrySection, false, logger,
                                "loot.enchantments." + enchantId + ".entries." + id);
                        if (entry != null) {
                            entries.put(id, entry);
                        }
                    }
                }
                modifiers.add(new LootModifier(enchantId.toLowerCase(Locale.ROOT), perLevel,
                        Set.copyOf(appliesTo), entries));
            }
        }

        double chanceCap = section.getDouble("chance-cap", 1.0);
        return new LootTable(Map.copyOf(base), Map.copyOf(tools), List.copyOf(modifiers), chanceCap);
    }

    private static LootEntry parseEntry(ConfigurationSection section, boolean materialRequired, Logger logger,
                                         String path) {
        Material material = null;
        String materialName = section.getString("material");
        if (materialName != null) {
            try {
                material = Material.valueOf(materialName.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                logger.warning("Unknown material '" + materialName + "' at " + path + ", ignoring entry.");
                return null;
            }
        } else if (materialRequired) {
            logger.warning("Missing material at " + path + ", ignoring entry.");
            return null;
        }
        double chance = section.getDouble("chance", 0);
        List<Integer> amount = section.getIntegerList("amount");
        int min = !amount.isEmpty() ? amount.get(0) : 0;
        int max = amount.size() > 1 ? amount.get(1) : min;
        return new LootEntry(material, chance, min, Math.max(min, max));
    }

    /** Independently rolls every entry id contributed by the base table, the tool used and any
     *  matching enchantments; returns the drops that hit. */
    public List<LootDrop> roll(RandomGenerator random, String toolMaterialName, Map<String, Integer> enchantLevels) {
        Map<String, LootEntry> toolEntries = toolMaterialName != null
                ? tools.getOrDefault(toolMaterialName, Map.of()) : Map.of();

        Set<String> ids = new LinkedHashSet<>(base.keySet());
        ids.addAll(toolEntries.keySet());
        for (LootModifier modifier : modifiers) {
            if (isActive(modifier, enchantLevels)) {
                ids.addAll(modifier.entries().keySet());
            }
        }

        List<LootDrop> drops = new ArrayList<>();
        for (String id : ids) {
            LootEntry b = base.get(id);
            LootEntry t = toolEntries.get(id);
            Material material = t != null && t.material() != null ? t.material()
                    : (b != null ? b.material() : null);
            if (material == null) {
                continue;
            }

            double chance = value(b, LootEntry::chance) + value(t, LootEntry::chance);
            double minAmount = value(b, LootEntry::minAmount) + value(t, LootEntry::minAmount);
            double maxAmount = value(b, LootEntry::maxAmount) + value(t, LootEntry::maxAmount);

            double multiplier = 1.0;
            for (LootModifier modifier : modifiers) {
                if (!isActive(modifier, enchantLevels)
                        || (!modifier.appliesTo().isEmpty() && !modifier.appliesTo().contains(id))) {
                    continue;
                }
                int level = enchantLevels.get(modifier.enchantId());
                multiplier *= 1.0 + modifier.perLevel() * level;
                LootEntry extra = modifier.entries().get(id);
                if (extra != null) {
                    chance += extra.chance() * level;
                    minAmount += extra.minAmount() * level;
                    maxAmount += extra.maxAmount() * level;
                }
            }

            double probability = Math.min(chance * multiplier, chanceCap);
            if (probability <= 0 || random.nextDouble() >= probability) {
                continue;
            }

            int min = (int) Math.round(minAmount);
            int max = Math.max(min, (int) Math.round(maxAmount));
            int amount = min == max ? min : min + random.nextInt(max - min + 1);
            if (amount > 0) {
                drops.add(new LootDrop(material, amount));
            }
        }
        return drops;
    }

    private boolean isActive(LootModifier modifier, Map<String, Integer> enchantLevels) {
        Integer level = enchantLevels.get(modifier.enchantId());
        return level != null && level > 0;
    }

    private static double value(LootEntry entry, ToDoubleFunction<LootEntry> field) {
        return entry != null ? field.applyAsDouble(entry) : 0;
    }

    public record LootDrop(Material material, int amount) {
    }
}
