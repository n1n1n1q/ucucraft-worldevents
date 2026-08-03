package net.ucucraft.worldevents.events.endstone.region;

/**
 * 3-4 chamfer distance transform: two flat raster scans, no allocation beyond the output array,
 * and near-Euclidean distance rather than the diamond-shaped bias a BFS queue would produce.
 * Divide the result by 3.0 to get an approximate real distance in grid cells.
 */
public final class DistanceField {

    private static final int INF = Integer.MAX_VALUE / 2;

    private DistanceField() {
    }

    public static int[] chamfer(boolean[] occupied, int width, int height) {
        int[] dist = new int[width * height];
        for (int i = 0; i < dist.length; i++) {
            dist[i] = occupied[i] ? 0 : INF;
        }

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int idx = y * width + x;
                int d = dist[idx];
                if (x > 0) {
                    d = Math.min(d, dist[idx - 1] + 3);
                }
                if (y > 0) {
                    d = Math.min(d, dist[idx - width] + 3);
                    if (x > 0) {
                        d = Math.min(d, dist[idx - width - 1] + 4);
                    }
                    if (x < width - 1) {
                        d = Math.min(d, dist[idx - width + 1] + 4);
                    }
                }
                dist[idx] = d;
            }
        }

        for (int y = height - 1; y >= 0; y--) {
            for (int x = width - 1; x >= 0; x--) {
                int idx = y * width + x;
                int d = dist[idx];
                if (x < width - 1) {
                    d = Math.min(d, dist[idx + 1] + 3);
                }
                if (y < height - 1) {
                    d = Math.min(d, dist[idx + width] + 3);
                    if (x < width - 1) {
                        d = Math.min(d, dist[idx + width + 1] + 4);
                    }
                    if (x > 0) {
                        d = Math.min(d, dist[idx + width - 1] + 4);
                    }
                }
                dist[idx] = d;
            }
        }

        return dist;
    }
}
