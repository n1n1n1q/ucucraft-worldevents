package net.ucucraft.worldevents.events.endstone;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import net.ucucraft.worldevents.events.endstone.backup.BlightManifest;
import net.ucucraft.worldevents.events.endstone.region.ChunkCoord;
import net.ucucraft.worldevents.events.endstone.region.RegionPlan;
import org.bukkit.World;

/**
 * The live state of one active (or restoring) blight. {@code affected} is a sorted array of packed
 * chunk keys covering core and infected chunks alike, so block-break handling can reject almost every
 * break in the world with a bbox check plus one binary search.
 */
public final class BlightRun {

    private final String runId;
    private final String eventId;
    private final String displayName;
    private final World world;
    private final RegionPlan plan;
    private final BlightSettings settings;
    private final BlightManifest manifest;
    private final AtomicBoolean teardownRequested = new AtomicBoolean();

    private final int minChunkX;
    private final int minChunkZ;
    private final int maxChunkX;
    private final int maxChunkZ;
    private final long[] affected;

    public BlightRun(String runId, String eventId, String displayName, World world, RegionPlan plan,
                      BlightSettings settings, BlightManifest manifest) {
        this.runId = runId;
        this.eventId = eventId;
        this.displayName = displayName;
        this.world = world;
        this.plan = plan;
        this.settings = settings;
        this.manifest = manifest;

        Set<ChunkCoord> all = new LinkedHashSet<>(plan.core());
        all.addAll(plan.infected().keySet());

        int minX = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        long[] keys = new long[all.size()];
        int i = 0;
        for (ChunkCoord c : all) {
            minX = Math.min(minX, c.x());
            maxX = Math.max(maxX, c.x());
            minZ = Math.min(minZ, c.z());
            maxZ = Math.max(maxZ, c.z());
            keys[i++] = c.packed();
        }
        Arrays.sort(keys);

        this.affected = keys;
        this.minChunkX = minX;
        this.minChunkZ = minZ;
        this.maxChunkX = maxX;
        this.maxChunkZ = maxZ;
    }

    public boolean contains(int chunkX, int chunkZ) {
        if (chunkX < minChunkX || chunkX > maxChunkX || chunkZ < minChunkZ || chunkZ > maxChunkZ) {
            return false;
        }
        return Arrays.binarySearch(affected, ChunkCoord.pack(chunkX, chunkZ)) >= 0;
    }

    public String runId() {
        return runId;
    }

    public String eventId() {
        return eventId;
    }

    public String displayName() {
        return displayName;
    }

    public AtomicBoolean teardownRequested() {
        return teardownRequested;
    }

    public World world() {
        return world;
    }

    public RegionPlan plan() {
        return plan;
    }

    public BlightSettings settings() {
        return settings;
    }

    public BlightManifest manifest() {
        return manifest;
    }

    public long[] affectedChunks() {
        return affected;
    }

    public int minChunkX() {
        return minChunkX;
    }

    public int minChunkZ() {
        return minChunkZ;
    }

    public int maxChunkX() {
        return maxChunkX;
    }

    public int maxChunkZ() {
        return maxChunkZ;
    }
}
