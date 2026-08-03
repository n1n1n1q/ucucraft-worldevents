package net.ucucraft.worldevents.events.endstone.backup;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.random.RandomGenerator;

import org.bukkit.ChunkSnapshot;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;

/**
 * Turns an immutable {@link ChunkSnapshot} into a {@link ChunkBackup} of the positions a conversion
 * would change. Snapshots are thread-safe by design, so the actual scan (and the RNG gate for infected
 * chunks) can run off the main thread; only capturing the snapshot and tile-entity positions needs it.
 */
public final class SnapshotScanner {

    private SnapshotScanner() {
    }

    /**
     * @param worldMinY         the world's {@code getMinHeight()}; needed only to translate a world-Y
     *                          section number into the 0-based index {@link ChunkSnapshot#isSectionEmpty}
     *                          expects (that method indexes its array directly, unlike {@code getBlockType})
     * @param convertible       indexed by {@link Material#ordinal()}: true when that material converts
     * @param excludedPositions packed positions (see {@link BackupCodec#packPosition}) to never touch,
     *                          typically block-entity locations captured on the main thread
     * @param chance            per-block conversion chance: 1.0 for the core, {@code chance*decay^(ring-1)}
     *                          for an infected chunk
     */
    public static ChunkBackup scan(ChunkSnapshot snapshot, UUID worldId, int chunkX, int chunkZ,
                                    int minY, int maxY, int worldMinY, Material conversionMaterial,
                                    boolean[] convertible, Set<Integer> excludedPositions, double chance,
                                    RandomGenerator random) {
        List<String> palette = new ArrayList<>();
        Map<String, Short> paletteIndex = new HashMap<>();
        List<Integer> positions = new ArrayList<>();
        List<Short> indices = new ArrayList<>();

        int minSection = Math.floorDiv(minY, 16);
        int maxSection = Math.floorDiv(maxY, 16);
        // isSectionEmpty indexes empty[] directly, so it wants a 0-based index from the world's bottom
        // section, not the world-Y section number the rest of the snapshot API uses.
        int sectionIndexOffset = Math.floorDiv(worldMinY, 16);

        for (int section = minSection; section <= maxSection; section++) {
            if (snapshot.isSectionEmpty(section - sectionIndexOffset)) {
                continue;
            }
            int sectionMinY = Math.max(minY, section * 16);
            int sectionMaxY = Math.min(maxY, section * 16 + 15);
            for (int y = sectionMaxY; y >= sectionMinY; y--) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        Material type = snapshot.getBlockType(x, y, z);
                        if (type == conversionMaterial || !convertible[type.ordinal()]) {
                            continue;
                        }
                        int packed = BackupCodec.packPosition(x, z, y, minY);
                        if (excludedPositions.contains(packed)) {
                            continue;
                        }
                        if (chance < 1.0 && random.nextDouble() >= chance) {
                            continue;
                        }
                        BlockData data = snapshot.getBlockData(x, y, z);
                        String key = data.getAsString();
                        Short idx = paletteIndex.get(key);
                        if (idx == null) {
                            idx = (short) palette.size();
                            palette.add(key);
                            paletteIndex.put(key, idx);
                        }
                        positions.add(packed);
                        indices.add(idx);
                    }
                }
            }
        }

        int[] positionArray = new int[positions.size()];
        short[] indexArray = new short[indices.size()];
        for (int i = 0; i < positionArray.length; i++) {
            positionArray[i] = positions.get(i);
            indexArray[i] = indices.get(i);
        }
        return new ChunkBackup(snapshot.getWorldName(), worldId, chunkX, chunkZ, minY, maxY,
                List.copyOf(palette), positionArray, indexArray);
    }
}
