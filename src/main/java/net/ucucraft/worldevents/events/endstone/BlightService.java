package net.ucucraft.worldevents.events.endstone;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.random.RandomGenerator;

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.ucucraft.worldevents.event.StopReason;
import net.ucucraft.worldevents.events.endstone.backup.BackupCodec;
import net.ucucraft.worldevents.events.endstone.backup.BackupStore;
import net.ucucraft.worldevents.events.endstone.backup.BlightManifest;
import net.ucucraft.worldevents.events.endstone.backup.ChunkBackup;
import net.ucucraft.worldevents.events.endstone.backup.ManifestState;
import net.ucucraft.worldevents.events.endstone.backup.SnapshotScanner;
import net.ucucraft.worldevents.events.endstone.block.BlockWriteEngine;
import net.ucucraft.worldevents.events.endstone.block.WriteJob;
import net.ucucraft.worldevents.events.endstone.loot.BlightMineListener;
import net.ucucraft.worldevents.events.endstone.outro.OutroSequence;
import net.ucucraft.worldevents.events.endstone.region.ChunkCoord;
import net.ucucraft.worldevents.events.endstone.region.ClaimSource;
import net.ucucraft.worldevents.events.endstone.region.CountriesClaims;
import net.ucucraft.worldevents.events.endstone.region.GridBounds;
import net.ucucraft.worldevents.events.endstone.region.RegionPlan;
import net.ucucraft.worldevents.events.endstone.region.RegionSelector;
import net.ucucraft.worldevents.lang.LangManager;
import net.ucucraft.worldevents.lang.Msg;
import org.bukkit.Bukkit;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Plugin-lifetime owner of every blight run. Not per-event state: {@code /we reload} rebuilds the
 * {@link net.ucucraft.worldevents.event.WorldEventManager}'s events, but a half-converted world still
 * needs an owner afterwards, so this service, its write engine and its listener all outlive reloads.
 * Only one run is ever active at a time, enforced by {@link #busy()}.
 */
public final class BlightService {

    public enum PrepareResult { OK, UNAVAILABLE, BUSY, NO_REGION }

    private final JavaPlugin plugin;
    private final LangManager lang;
    private final RandomGenerator random;
    private final ClaimSource claims = new CountriesClaims();
    private final RegionSelector selector;
    private final BlockWriteEngine engine;
    private final BlightMineListener mineListener;

    private volatile BlightRun activeRun;
    private OutroSequence activeOutro;

    private World pendingWorld;
    private RegionPlan pendingPlan;
    private BlightSettings pendingSettings;

    public BlightService(JavaPlugin plugin, LangManager lang, RandomGenerator random) {
        this.plugin = plugin;
        this.lang = lang;
        this.random = random;
        this.selector = new RegionSelector(random);
        this.engine = new BlockWriteEngine(plugin);
        this.mineListener = new BlightMineListener(random);
        plugin.getServer().getPluginManager().registerEvents(mineListener, plugin);
    }

    public boolean available() {
        return claims.available();
    }

    public boolean busy() {
        return activeRun != null;
    }

    /** Synchronous: resolves the world, picks a region and stashes it for {@link #begin}. Cheap
     *  (single-digit ms even on a full 512x512 grid), safe to call from {@code canStart}. */
    public PrepareResult prepare(BlightSettings settings) {
        if (busy()) {
            return PrepareResult.BUSY;
        }
        if (!claims.available()) {
            return PrepareResult.UNAVAILABLE;
        }

        Map<String, Set<ChunkCoord>> claimsByWorld = claims.claimsByWorld();
        World world = resolveWorld(settings.world(), claimsByWorld);
        if (world == null) {
            return PrepareResult.NO_REGION;
        }
        Set<ChunkCoord> worldClaims = claimsByWorld.getOrDefault(world.getName(), Set.of());
        if (worldClaims.isEmpty() && settings.region().requireClaims()) {
            return PrepareResult.NO_REGION;
        }

        GridBounds bounds = RegionSelector.computeBounds(world, worldClaims, settings.region().search());
        var plan = selector.select(world.getName(), bounds, worldClaims, settings.region(),
                (cx, cz) -> world.isChunkGenerated(cx, cz));
        if (plan.isEmpty()) {
            return PrepareResult.NO_REGION;
        }

        this.pendingWorld = world;
        this.pendingPlan = plan.get();
        this.pendingSettings = settings;
        return PrepareResult.OK;
    }

    private World resolveWorld(String name, Map<String, Set<ChunkCoord>> claimsByWorld) {
        if (name != null && !name.isBlank()) {
            return Bukkit.getWorld(name);
        }
        String best = null;
        int bestCount = -1;
        for (Map.Entry<String, Set<ChunkCoord>> entry : claimsByWorld.entrySet()) {
            if (entry.getValue().size() > bestCount) {
                bestCount = entry.getValue().size();
                best = entry.getKey();
            }
        }
        if (best != null) {
            return Bukkit.getWorld(best);
        }
        List<World> worlds = plugin.getServer().getWorlds();
        return worlds.isEmpty() ? null : worlds.get(0);
    }

    /** Consumes the state {@link #prepare} stashed and kicks off the backup + conversion pipeline. */
    public void begin(String eventId, String displayName) {
        World world = pendingWorld;
        RegionPlan plan = pendingPlan;
        BlightSettings settings = pendingSettings;
        pendingWorld = null;
        pendingPlan = null;
        pendingSettings = null;

        String runId = UUID.randomUUID().toString();
        BackupStore store = new BackupStore(plugin.getDataFolder(), settings.backup().directory(), plugin.getLogger());
        BlightManifest manifest = BlightManifest.create(store.manifestFile(runId), eventId, world.getName(),
                world.getUID(), null);
        manifest.save();

        BlightRun run = new BlightRun(runId, eventId, displayName, world, plan, settings, manifest);
        this.activeRun = run;

        mineListener.activate(run, settings.conversion().block(), settings.loot().table(), settings.loot().deliver(),
                settings.loot().dropEndstone(), settings.loot().requirePickaxe(), settings.loot().silkTouchBypasses());
        engine.configure(settings.engine().blocksPerTick(), settings.engine().maxMsPerTick());

        int minY = clampedMinY(world, settings);
        int maxY = clampedMaxY(world, settings);
        int worldMinY = world.getMinHeight();
        Material block = settings.conversion().block();
        boolean[] convertible = convertibleTable(settings);
        UUID worldId = world.getUID();

        List<PendingScan> scans = new ArrayList<>();
        for (ChunkCoord c : allChunks(plan)) {
            Chunk chunk = world.getChunkAt(c.x(), c.z());
            chunk.addPluginChunkTicket(plugin);
            ChunkSnapshot snapshot = chunk.getChunkSnapshot(false, false, false);
            Set<Integer> excluded = new HashSet<>();
            if (settings.conversion().skipBlockEntities()) {
                for (BlockState state : chunk.getTileEntities(false)) {
                    excluded.add(BackupCodec.packPosition(state.getX() & 0xF, state.getZ() & 0xF, state.getY(), minY));
                }
            }
            int ring = plan.infected().getOrDefault(c, 0);
            double chance = ring == 0 ? 1.0
                    : settings.region().infection().chance()
                    * Math.pow(settings.region().infection().decay(), ring - 1);
            scans.add(new PendingScan(c, snapshot, excluded, chance));
        }

        Location seedLocation = new Location(world, plan.seed().x() * 16 + 8, 0, plan.seed().z() * 16 + 8);
        lang.broadcast(Msg.BLIGHT_SPREADING, placeholders(displayName, eventId, seedLocation));

        // Scanning + gzip encoding is CPU work on immutable, thread-safe snapshots: run it off the main
        // thread. Only the snapshot/ticket/tile-entity capture above needed to happen on it.
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            List<ChunkBackup> backups = new ArrayList<>();
            for (PendingScan scan : scans) {
                ChunkBackup backup = SnapshotScanner.scan(scan.snapshot(), worldId, scan.coord().x(),
                        scan.coord().z(), minY, maxY, worldMinY, block, convertible, scan.excluded(),
                        scan.chance(), random);
                if (backup.entryCount() > 0) {
                    backups.add(backup);
                    store.writeChunk(runId, backup);
                }
            }

            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (activeRun != run) {
                    return;
                }
                if (run.teardownRequested().get()) {
                    beginRestore(run);
                    return;
                }
                manifest.state = ManifestState.CONVERTING;
                manifest.save();

                BlockData data = Bukkit.createBlockData(block);
                for (ChunkBackup backup : backups) {
                    engine.submit(WriteJob.uniform(run.world().getName(), backup.chunkX(), backup.chunkZ(),
                            backup.minY(), backup.positions(), data));
                }
                engine.onDrained(() -> {
                    if (activeRun != run) {
                        return;
                    }
                    if (run.teardownRequested().get()) {
                        beginRestore(run);
                        return;
                    }
                    manifest.state = ManifestState.ACTIVE;
                    manifest.save();
                    lang.broadcast(Msg.BLIGHT_ACTIVE, placeholders(displayName, eventId, seedLocation));
                    lang.broadcast(Msg.BLIGHT_LOCATION, placeholders(displayName, eventId, seedLocation));
                });
            });
        });
    }

    /** Called from {@code onStop} for every reason; the reason decides whether a cinematic plays. */
    public void end(StopReason reason) {
        BlightRun run = activeRun;
        if (run == null) {
            return;
        }
        if (activeOutro != null) {
            activeOutro.cancel();
            activeOutro = null;
        }
        mineListener.deactivate();

        switch (reason) {
            case FINISHED, COMMAND -> {
                if (run.settings().outro().enabled()) {
                    activeOutro = new OutroSequence(plugin, lang, run.settings().outro(), playersNear(run),
                            () -> beginRestore(run));
                    activeOutro.start();
                } else {
                    beginRestore(run);
                }
            }
            case RELOAD -> {
                run.teardownRequested().set(true);
                if (run.manifest().state == ManifestState.ACTIVE) {
                    beginRestore(run);
                }
                // Otherwise BACKING_UP/CONVERTING is in flight; its own continuation checks the flag.
            }
            case SHUTDOWN -> {
                run.teardownRequested().set(true);
                run.manifest().state = ManifestState.RESTORING;
                run.manifest().save();
                restoreBlocking(run);
            }
        }
    }

    /** Runs on the plugin's tick; currently just used to keep the event's own tick alive if needed. */
    public void tick() {
    }

    private void beginRestore(BlightRun run) {
        run.manifest().state = ManifestState.RESTORING;
        run.manifest().save();
        lang.broadcast(Msg.BLIGHT_RESTORING, placeholders(run.displayName(), run.eventId(), null));

        BackupStore store = new BackupStore(plugin.getDataFolder(), run.settings().backup().directory(),
                plugin.getLogger());
        boolean corrupt = submitRestoreJobs(store, run.runId());
        if (corrupt) {
            lang.broadcast(Msg.BLIGHT_BLOCKS_LOST_WARNING, placeholders(run.displayName(), run.eventId(), null));
        }

        String runId = run.runId();
        engine.onDrained(() -> {
            releaseTickets(run);
            store.deleteRun(runId);
            if (activeRun == run) {
                activeRun = null;
            }
            lang.broadcast(Msg.BLIGHT_RESTORED, placeholders(run.displayName(), run.eventId(), null));
        });
    }

    private void restoreBlocking(BlightRun run) {
        BackupStore store = new BackupStore(plugin.getDataFolder(), run.settings().backup().directory(),
                plugin.getLogger());
        submitRestoreJobs(store, run.runId());
        engine.drainBlocking(run.settings().engine().shutdownBudgetMs());
        releaseTickets(run);
        if (engine.idle()) {
            store.deleteRun(run.runId());
        }
        if (activeRun == run) {
            activeRun = null;
        }
    }

    /** Startup recovery: anything left behind by a crash gets replayed (idempotent) or, for a run that
     *  never got past backing up, simply deleted since nothing was ever converted. */
    public void recover(Collection<String> directories) {
        for (String directory : directories) {
            BackupStore store = new BackupStore(plugin.getDataFolder(), directory, plugin.getLogger());
            for (String runId : store.listRunIds()) {
                recoverRun(store, runId);
            }
        }
    }

    private void recoverRun(BackupStore store, String runId) {
        File manifestFile = store.manifestFile(runId);
        if (!manifestFile.isFile()) {
            plugin.getLogger().warning("Blight run '" + runId + "' has no manifest; quarantining.");
            store.quarantine(runId);
            return;
        }

        BlightManifest manifest;
        try {
            manifest = BlightManifest.load(manifestFile);
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "Corrupt blight manifest for run '" + runId + "'", e);
            store.quarantine(runId);
            return;
        }

        if (manifest.state == ManifestState.BACKING_UP) {
            store.deleteRun(runId);
            return;
        }

        World world = Bukkit.getWorld(manifest.world);
        if (world == null || !world.getUID().equals(manifest.worldId)) {
            plugin.getLogger().warning("World '" + manifest.world + "' for blight run '" + runId
                    + "' is missing or was recreated; keeping the backup for manual recovery.");
            return;
        }

        manifest.state = ManifestState.RESTORING;
        manifest.save();
        boolean corrupt = submitRestoreJobs(store, runId);
        if (corrupt) {
            plugin.getLogger().warning("Some blocks from blight run '" + runId + "' could not be restored.");
        }
        plugin.getLogger().info("Recovered an unfinished blight restore for run '" + runId + "'.");
        engine.onDrained(() -> store.deleteRun(runId));
    }

    private boolean submitRestoreJobs(BackupStore store, String runId) {
        boolean corrupt = false;
        for (File file : store.listChunkFiles(runId)) {
            try {
                ChunkBackup backup = store.readChunk(file);
                engine.submit(new WriteJob(backup.world(), backup.chunkX(), backup.chunkZ(), backup.minY(),
                        backup.positions(), backup.paletteIndex(), backup.palette()));
            } catch (IOException e) {
                corrupt = true;
                plugin.getLogger().log(Level.WARNING, "Corrupt blight backup " + file, e);
            }
        }
        return corrupt;
    }

    /** Drains whatever is already queued, blocking: called only from {@code onDisable}, where the
     *  scheduler no longer accepts new tasks. */
    public void drainOnDisable(long budgetMs) {
        engine.drainBlocking(budgetMs);
    }

    private void releaseTickets(BlightRun run) {
        World world = run.world();
        for (ChunkCoord c : allChunks(run.plan())) {
            if (world.isChunkLoaded(c.x(), c.z())) {
                world.getChunkAt(c.x(), c.z()).removePluginChunkTicket(plugin);
            }
        }
    }

    private List<UUID> playersNear(BlightRun run) {
        int radius = run.settings().outro().radiusChunks();
        List<UUID> ids = new ArrayList<>();
        for (Player player : run.world().getPlayers()) {
            int cx = player.getLocation().getBlockX() >> 4;
            int cz = player.getLocation().getBlockZ() >> 4;
            if (cx >= run.minChunkX() - radius && cx <= run.maxChunkX() + radius
                    && cz >= run.minChunkZ() - radius && cz <= run.maxChunkZ() + radius) {
                ids.add(player.getUniqueId());
            }
        }
        return ids;
    }

    private boolean[] convertibleTable(BlightSettings settings) {
        boolean[] table = new boolean[Material.values().length];
        for (Material material : Material.values()) {
            // Snapshot#getBlockType only ever returns modern materials, so legacy slots are never read.
            // Calling isBlock() on a legacy material forces Mojang's legacy datafixer to initialise on the
            // main thread (a multi-second stall + "DO NOT REPORT THIS TO PAPER" thread dump), so skip them.
            if (material.isLegacy()) {
                continue;
            }
            table[material.ordinal()] = material.isBlock() && material.isSolid()
                    && !settings.conversion().keep().contains(material);
        }
        return table;
    }

    private int clampedMinY(World world, BlightSettings settings) {
        return Math.max(settings.conversion().minY(), world.getMinHeight());
    }

    private int clampedMaxY(World world, BlightSettings settings) {
        return Math.min(settings.conversion().maxY(), world.getMaxHeight() - 1);
    }

    private Set<ChunkCoord> allChunks(RegionPlan plan) {
        Set<ChunkCoord> all = new LinkedHashSet<>(plan.core());
        all.addAll(plan.infected().keySet());
        return all;
    }

    private TagResolver[] placeholders(String displayName, String eventId, Location location) {
        if (location == null) {
            return new TagResolver[]{
                    Placeholder.unparsed("event", displayName),
                    Placeholder.unparsed("id", eventId)
            };
        }
        String text = location.getWorld().getName() + " (" + location.getBlockX() + ", " + location.getBlockZ() + ")";
        return new TagResolver[]{
                Placeholder.unparsed("event", displayName),
                Placeholder.unparsed("id", eventId),
                Placeholder.unparsed("location", text)
        };
    }

    private record PendingScan(ChunkCoord coord, ChunkSnapshot snapshot, Set<Integer> excluded, double chance) {
    }
}
