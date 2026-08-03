package net.ucucraft.worldevents.events.endstone.region;

import java.util.Map;
import java.util.Set;

/**
 * The outcome of a region selection: a fully-converted {@code core} plus a halo of {@code infected}
 * chunks mapped to their ring depth (1-based), which the scanner uses to fade the conversion chance.
 */
public record RegionPlan(String world, ChunkCoord seed, Set<ChunkCoord> core, Map<ChunkCoord, Integer> infected) {

    public int size() {
        return core.size() + infected.size();
    }
}
