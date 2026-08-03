package net.ucucraft.worldevents.events.endstone.backup;

import java.util.List;
import java.util.UUID;

/**
 * The original state of every block a conversion is about to change, for one chunk.
 * {@code positions[i]} packs {@code ((x&15)<<20)|((z&15)<<16)|((y-minY)&0xFFFF)}; {@code paletteIndex[i]}
 * points into {@code palette} ({@link org.bukkit.block.data.BlockData#getAsString()} form).
 */
public record ChunkBackup(String world, UUID worldId, int chunkX, int chunkZ, int minY, int maxY,
                           List<String> palette, int[] positions, short[] paletteIndex) {

    public int entryCount() {
        return positions.length;
    }
}
