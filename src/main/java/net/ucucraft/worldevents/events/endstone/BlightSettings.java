package net.ucucraft.worldevents.events.endstone;

import java.time.Duration;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Logger;

import net.ucucraft.worldevents.events.endstone.backup.RestoreMode;
import net.ucucraft.worldevents.events.endstone.loot.DeliveryMode;
import net.ucucraft.worldevents.events.endstone.loot.LootTable;
import net.ucucraft.worldevents.util.Durations;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;

/** Single parse point for the whole {@code settings:} block of an {@code endstone-blight} event. */
public record BlightSettings(
        String world,
        RegionSettings region,
        ConversionSettings conversion,
        EngineSettings engine,
        BackupSettings backup,
        LootSettings loot,
        OutroSettings outro
) {

    public record RegionSettings(int sizeMin, int sizeMax, double compactness, int maxSeedAttempts,
                                  boolean requireGenerated, boolean allowClaimed, boolean requireClaims,
                                  SearchSettings search, WeightSettings weight, InfectionSettings infection) {
    }

    public record SearchSettings(int marginChunks, int maxRadiusChunks, int maxCells) {
    }

    public record WeightSettings(double minDistanceChunks, double capDistanceChunks, double exponent,
                                  double baseWeight) {
    }

    public record InfectionSettings(int rings, double chance, double decay, boolean loot) {
    }

    public record ConversionSettings(Material block, int minY, int maxY, boolean skipBlockEntities,
                                      Set<Material> keep) {
    }

    public record EngineSettings(int blocksPerTick, double maxMsPerTick, boolean resyncChunks,
                                  long shutdownBudgetMs) {
    }

    public record BackupSettings(String directory, RestoreMode restoreMode, boolean quarantineBroken) {
    }

    public record LootSettings(LootTable table, DeliveryMode deliver, boolean dropEndstone,
                                boolean requirePickaxe, boolean silkTouchBypasses) {
    }

    public record OutroSettings(boolean enabled, int radiusChunks, Duration darknessDelay,
                                 Duration darknessDuration, Duration portalDuration, int portalParticles,
                                 boolean clearEffects, Duration clearDelay, Duration restoreDelay) {
    }

    public static BlightSettings parse(ConfigurationSection root, Logger logger) {
        ConfigurationSection s = root != null ? root : new MemoryConfiguration();
        String world = s.getString("world", "");

        ConfigurationSection regionSection = section(s, "region");
        ConfigurationSection searchSection = section(regionSection, "search");
        ConfigurationSection weightSection = section(regionSection, "weight");
        ConfigurationSection infectionSection = section(regionSection, "infection");

        double minDist = weightSection.getDouble("min-distance-chunks", 10);
        double capDist = Math.max(minDist + 1, weightSection.getDouble("cap-distance-chunks", 96));

        RegionSettings region = new RegionSettings(
                Math.max(1, regionSection.getInt("size-min", 8)),
                Math.max(regionSection.getInt("size-min", 8), regionSection.getInt("size-max", 14)),
                Math.max(0.01, regionSection.getDouble("compactness", 1.6)),
                Math.max(1, regionSection.getInt("max-seed-attempts", 24)),
                regionSection.getBoolean("require-generated", true),
                regionSection.getBoolean("allow-claimed", false),
                regionSection.getBoolean("require-claims", false),
                new SearchSettings(
                        Math.max(1, searchSection.getInt("margin-chunks", 96)),
                        Math.max(1, searchSection.getInt("max-radius-chunks", 384)),
                        Math.max(64, searchSection.getInt("max-cells", 262144))),
                new WeightSettings(minDist, capDist,
                        Math.max(0.01, weightSection.getDouble("exponent", 2.0)),
                        clamp01(weightSection.getDouble("base-weight", 0.05))),
                new InfectionSettings(
                        Math.max(0, infectionSection.getInt("rings", 1)),
                        clamp01(infectionSection.getDouble("chance", 0.35)),
                        clamp01(infectionSection.getDouble("decay", 0.5)),
                        infectionSection.getBoolean("loot", true)));

        ConfigurationSection conversionSection = section(s, "conversion");
        Material block = materialOr(conversionSection.getString("block", "END_STONE"), Material.END_STONE, logger);
        Set<Material> keep = new HashSet<>();
        for (String entry : conversionSection.getStringList("keep")) {
            expandKeep(entry, keep, logger);
        }
        ConversionSettings conversion = new ConversionSettings(
                block,
                conversionSection.getInt("min-y", -64),
                conversionSection.getInt("max-y", 320),
                conversionSection.getBoolean("skip-block-entities", true),
                Set.copyOf(keep));

        ConfigurationSection engineSection = section(s, "engine");
        EngineSettings engine = new EngineSettings(
                Math.max(1, engineSection.getInt("blocks-per-tick", 3000)),
                Math.max(0.1, engineSection.getDouble("max-ms-per-tick", 3.0)),
                engineSection.getBoolean("resync-chunks", false),
                Math.max(0, engineSection.getLong("shutdown-budget-ms", 20000)));

        ConfigurationSection backupSection = section(s, "backup");
        BackupSettings backup = new BackupSettings(
                backupSection.getString("directory", "blight"),
                restoreModeOr(backupSection.getString("restore-mode", "ALL"), logger),
                backupSection.getBoolean("quarantine-broken", true));

        ConfigurationSection lootSection = section(s, "loot");
        LootTable table = LootTable.parse(lootSection, logger);
        LootSettings loot = new LootSettings(
                table,
                deliveryModeOr(lootSection.getString("deliver", "DROP"), logger),
                lootSection.getBoolean("drop-endstone", false),
                lootSection.getBoolean("require-pickaxe", true),
                lootSection.getString("silk-touch", "NO_LOOT").equalsIgnoreCase("NO_LOOT"));

        ConfigurationSection outroSection = section(s, "outro");
        OutroSettings outro = new OutroSettings(
                outroSection.getBoolean("enabled", true),
                Math.max(0, outroSection.getInt("radius-chunks", 5)),
                durationOr(outroSection.getString("darkness-delay"), Duration.ofSeconds(4)),
                durationOr(outroSection.getString("darkness-duration"), Duration.ofSeconds(12)),
                durationOr(outroSection.getString("portal-duration"), Duration.ofSeconds(3)),
                Math.max(0, outroSection.getInt("portal-particles", 60)),
                outroSection.getBoolean("clear-effects", true),
                durationOr(outroSection.getString("clear-delay"), Duration.ofSeconds(1)),
                durationOr(outroSection.getString("restore-delay"), Duration.ofSeconds(2)));

        return new BlightSettings(world, region, conversion, engine, backup, loot, outro);
    }

    private static ConfigurationSection section(ConfigurationSection parent, String path) {
        ConfigurationSection section = parent.getConfigurationSection(path);
        return section != null ? section : new MemoryConfiguration();
    }

    private static double clamp01(double value) {
        return Math.max(0, Math.min(1, value));
    }

    private static Duration durationOr(String input, Duration fallback) {
        return input == null ? fallback : Durations.parseOr(input, fallback);
    }

    private static Material materialOr(String name, Material fallback, Logger logger) {
        if (name == null) {
            return fallback;
        }
        try {
            return Material.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            logger.warning("Unknown material '" + name + "', using " + fallback + ".");
            return fallback;
        }
    }

    private static RestoreMode restoreModeOr(String name, Logger logger) {
        try {
            return RestoreMode.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            logger.warning("Unknown restore-mode '" + name + "', using ALL.");
            return RestoreMode.ALL;
        }
    }

    private static DeliveryMode deliveryModeOr(String name, Logger logger) {
        try {
            return DeliveryMode.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            logger.warning("Unknown loot.deliver '" + name + "', using DROP.");
            return DeliveryMode.DROP;
        }
    }

    /** A {@code keep} entry is either a plain material or a {@code #namespace:tag} block tag. */
    private static void expandKeep(String entry, Set<Material> keep, Logger logger) {
        if (entry == null || entry.isBlank()) {
            return;
        }
        if (entry.startsWith("#")) {
            String key = entry.substring(1);
            int colon = key.indexOf(':');
            String path = colon >= 0 ? key.substring(colon + 1) : key;
            org.bukkit.NamespacedKey tagKey = org.bukkit.NamespacedKey.minecraft(path);
            org.bukkit.Tag<Material> tag = org.bukkit.Bukkit.getTag(org.bukkit.Tag.REGISTRY_BLOCKS, tagKey,
                    Material.class);
            if (tag == null) {
                logger.warning("Unknown block tag '" + entry + "' in conversion.keep, ignoring.");
                return;
            }
            keep.addAll(tag.getValues());
            return;
        }
        try {
            keep.add(Material.valueOf(entry.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            logger.warning("Unknown material '" + entry + "' in conversion.keep, ignoring.");
        }
    }
}
