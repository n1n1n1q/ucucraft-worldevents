package net.ucucraft.worldevents.events.endstone.region;

import java.util.Map;
import java.util.Set;

/** Supplies claimed-chunk data used to pick "wilderness". Implementations must never throw. */
public interface ClaimSource {

    /** Whether claim data can currently be provided at all. */
    boolean available();

    /** Claimed chunks grouped by world name. Empty when unavailable. */
    Map<String, Set<ChunkCoord>> claimsByWorld();
}
