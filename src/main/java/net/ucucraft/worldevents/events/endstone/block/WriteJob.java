package net.ucucraft.worldevents.events.endstone.block;

import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;

/**
 * One chunk's worth of queued block writes for {@link BlockWriteEngine}. Positions are packed as
 * {@code ((x&15)<<20)|((z&15)<<16)|((y-minY)&0xFFFF)} so both conversion and restore share one
 * representation and one write loop; the only difference is whether every entry points at the same
 * palette slot (conversion) or a per-position one (restore).
 */
public final class WriteJob {

    private final String world;
    private final int chunkX;
    private final int chunkZ;
    private final int minY;
    private final int[] positions;
    private final short[] dataIndex;
    private final List<String> palette;

    private BlockData[] resolved;
    private Chunk chunk;
    private int cursor;

    public WriteJob(String world, int chunkX, int chunkZ, int minY, int[] positions, short[] dataIndex,
                     List<String> palette) {
        this.world = world;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.minY = minY;
        this.positions = positions;
        this.dataIndex = dataIndex;
        this.palette = palette;
    }

    /** Every position becomes the same block: what conversion needs. */
    public static WriteJob uniform(String world, int chunkX, int chunkZ, int minY, int[] positions, BlockData data) {
        WriteJob job = new WriteJob(world, chunkX, chunkZ, minY, positions, new short[positions.length],
                List.of(data.getAsString()));
        job.resolved = new BlockData[]{data};
        return job;
    }

    public String world() {
        return world;
    }

    public int chunkX() {
        return chunkX;
    }

    public int chunkZ() {
        return chunkZ;
    }

    public boolean done() {
        return cursor >= positions.length;
    }

    public int size() {
        return positions.length;
    }

    /** Applies up to {@code budget} writes; returns how many were actually applied. */
    int apply(int budget) {
        if (chunk == null) {
            World w = Bukkit.getWorld(world);
            if (w == null) {
                cursor = positions.length;
                return 0;
            }
            chunk = w.getChunkAt(chunkX, chunkZ);
        }
        if (resolved == null) {
            resolved = new BlockData[palette.size()];
            for (int i = 0; i < palette.size(); i++) {
                resolved[i] = Bukkit.createBlockData(palette.get(i));
            }
        }
        int applied = 0;
        while (cursor < positions.length && applied < budget) {
            int packed = positions[cursor];
            int localX = (packed >> 20) & 0xF;
            int localZ = (packed >> 16) & 0xF;
            int y = (packed & 0xFFFF) + minY;
            chunk.getBlock(localX, y, localZ).setBlockData(resolved[dataIndex[cursor]], false);
            cursor++;
            applied++;
        }
        return applied;
    }
}
