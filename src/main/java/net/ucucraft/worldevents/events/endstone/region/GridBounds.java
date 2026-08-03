package net.ucucraft.worldevents.events.endstone.region;

/** The chunk-coordinate window the region search operates on. */
public record GridBounds(int minX, int minZ, int width, int height) {

    public boolean contains(int cx, int cz) {
        return cx >= minX && cz >= minZ && cx < minX + width && cz < minZ + height;
    }

    public int localX(int cx) {
        return cx - minX;
    }

    public int localZ(int cz) {
        return cz - minZ;
    }

    public int index(int localX, int localZ) {
        return localZ * width + localX;
    }

    public int clampX(int cx) {
        return Math.max(minX, Math.min(minX + width - 1, cx));
    }

    public int clampZ(int cz) {
        return Math.max(minZ, Math.min(minZ + height - 1, cz));
    }
}
