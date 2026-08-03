package net.ucucraft.worldevents.events.endstone.region;

/** A chunk coordinate within a single world (the world is tracked by whoever holds the coordinate). */
public record ChunkCoord(int x, int z) {

    public static long pack(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    public static int unpackX(long packed) {
        return (int) (packed >> 32);
    }

    public static int unpackZ(long packed) {
        return (int) packed;
    }

    public long packed() {
        return pack(x, z);
    }
}
