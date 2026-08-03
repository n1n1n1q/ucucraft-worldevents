package net.ucucraft.worldevents.events.endstone.region;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.random.RandomGenerator;

import net.ucucraft.worldevents.events.endstone.BlightSettings.RegionSettings;
import net.ucucraft.worldevents.events.endstone.BlightSettings.SearchSettings;
import net.ucucraft.worldevents.events.endstone.BlightSettings.WeightSettings;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldBorder;

/**
 * Picks a connected, randomly-shaped patch of deep wilderness: a chamfer distance field from every
 * claim turns into a weight plateau, a weighted-reservoir pick chooses the seed, and a frontier growth
 * step expands it into the core region plus an infected halo.
 */
public final class RegionSelector {

    @FunctionalInterface
    public interface GeneratedCheck {
        boolean isGenerated(int chunkX, int chunkZ);
    }

    private final RandomGenerator random;

    public RegionSelector(RandomGenerator random) {
        this.random = random;
    }

    /** Computes the chunk-coordinate search window: claim bbox (or spawn) grown by margin, clamped to
     *  the spawn radius, the world border and a cell budget. */
    public static GridBounds computeBounds(World world, Set<ChunkCoord> claims, SearchSettings search) {
        Location spawn = world.getSpawnLocation();
        int scx = spawn.getBlockX() >> 4;
        int scz = spawn.getBlockZ() >> 4;

        int minX, minZ, maxX, maxZ;
        if (claims.isEmpty()) {
            minX = scx - search.marginChunks();
            maxX = scx + search.marginChunks();
            minZ = scz - search.marginChunks();
            maxZ = scz + search.marginChunks();
        } else {
            minX = Integer.MAX_VALUE;
            minZ = Integer.MAX_VALUE;
            maxX = Integer.MIN_VALUE;
            maxZ = Integer.MIN_VALUE;
            for (ChunkCoord c : claims) {
                minX = Math.min(minX, c.x());
                maxX = Math.max(maxX, c.x());
                minZ = Math.min(minZ, c.z());
                maxZ = Math.max(maxZ, c.z());
            }
            minX -= search.marginChunks();
            maxX += search.marginChunks();
            minZ -= search.marginChunks();
            maxZ += search.marginChunks();
        }

        minX = Math.max(minX, scx - search.maxRadiusChunks());
        maxX = Math.min(maxX, scx + search.maxRadiusChunks());
        minZ = Math.max(minZ, scz - search.maxRadiusChunks());
        maxZ = Math.min(maxZ, scz + search.maxRadiusChunks());

        WorldBorder border = world.getWorldBorder();
        double half = border.getSize() / 2.0;
        Location center = border.getCenter();
        int borderMinX = (int) Math.floor(center.getX() - half) >> 4;
        int borderMaxX = (int) Math.ceil(center.getX() + half) >> 4;
        int borderMinZ = (int) Math.floor(center.getZ() - half) >> 4;
        int borderMaxZ = (int) Math.ceil(center.getZ() + half) >> 4;
        minX = Math.max(minX, borderMinX);
        maxX = Math.min(maxX, borderMaxX);
        minZ = Math.max(minZ, borderMinZ);
        maxZ = Math.min(maxZ, borderMaxZ);

        if (maxX < minX) {
            maxX = minX;
        }
        if (maxZ < minZ) {
            maxZ = minZ;
        }

        int width = maxX - minX + 1;
        int height = maxZ - minZ + 1;
        if ((long) width * height > search.maxCells()) {
            double scale = Math.sqrt(search.maxCells() / ((double) width * height));
            int cx = (minX + maxX) / 2;
            int cz = (minZ + maxZ) / 2;
            int halfW = Math.max(1, (int) (width * scale / 2));
            int halfH = Math.max(1, (int) (height * scale / 2));
            minX = cx - halfW;
            maxX = cx + halfW;
            minZ = cz - halfH;
            maxZ = cz + halfH;
            width = maxX - minX + 1;
            height = maxZ - minZ + 1;
        }

        return new GridBounds(minX, minZ, width, height);
    }

    public Optional<RegionPlan> select(String world, GridBounds bounds, Set<ChunkCoord> claims,
                                        RegionSettings settings, GeneratedCheck generated) {
        int w = bounds.width();
        int h = bounds.height();
        boolean[] occupied = new boolean[w * h];
        for (ChunkCoord c : claims) {
            int lx = bounds.clampX(c.x()) - bounds.minX();
            int lz = bounds.clampZ(c.z()) - bounds.minZ();
            occupied[lz * w + lx] = true;
        }

        int[] chamfer = DistanceField.chamfer(occupied, w, h);
        double[] weight = new double[w * h];
        for (int i = 0; i < weight.length; i++) {
            weight[i] = weight(chamfer[i] / 3.0, settings.weight());
        }

        Map<Long, Boolean> generatedCache = new HashMap<>();

        for (int attempt = 0; attempt < settings.maxSeedAttempts(); attempt++) {
            ChunkCoord seed = pickWeighted(bounds, weight);
            if (seed == null) {
                return Optional.empty();
            }
            int seedIdx = bounds.index(bounds.localX(seed.x()), bounds.localZ(seed.z()));
            if (!isValidCandidate(seed, bounds, weight, occupied, settings, generated, generatedCache)) {
                weight[seedIdx] = 0;
                continue;
            }

            RegionPlan plan = grow(world, seed, bounds, weight, occupied, settings, generated, generatedCache);
            if (plan != null) {
                return Optional.of(plan);
            }
            weight[seedIdx] = 0;
        }
        return Optional.empty();
    }

    private RegionPlan grow(String world, ChunkCoord seed, GridBounds bounds, double[] weight, boolean[] occupied,
                             RegionSettings settings, GeneratedCheck generated, Map<Long, Boolean> generatedCache) {
        int span = settings.sizeMax() - settings.sizeMin();
        int target = settings.sizeMin() + (span > 0 ? random.nextInt(span + 1) : 0);

        Set<ChunkCoord> selected = new LinkedHashSet<>();
        Map<ChunkCoord, Integer> adjCount = new HashMap<>();
        Set<ChunkCoord> frontier = new LinkedHashSet<>();

        selected.add(seed);
        pushNeighbours(seed, bounds, selected, frontier, adjCount);

        while (selected.size() < target && !frontier.isEmpty()) {
            ChunkCoord next = pickFromFrontier(frontier, bounds, weight, adjCount, settings.compactness());
            frontier.remove(next);
            if (!isValidCandidate(next, bounds, weight, occupied, settings, generated, generatedCache)) {
                continue;
            }
            selected.add(next);
            pushNeighbours(next, bounds, selected, frontier, adjCount);
        }

        if (selected.size() < settings.sizeMin()) {
            return null;
        }

        Map<ChunkCoord, Integer> infected =
                computeInfection(selected, bounds, weight, occupied, settings, generated, generatedCache);
        return new RegionPlan(world, seed, Set.copyOf(selected), infected);
    }

    private ChunkCoord pickFromFrontier(Set<ChunkCoord> frontier, GridBounds bounds, double[] weight,
                                         Map<ChunkCoord, Integer> adjCount, double compactness) {
        double total = 0;
        ChunkCoord chosen = null;
        for (ChunkCoord c : frontier) {
            int idx = bounds.index(bounds.localX(c.x()), bounds.localZ(c.z()));
            int adjacent = adjCount.getOrDefault(c, 1);
            double score = weight[idx] * Math.pow(compactness, adjacent - 1);
            if (score <= 0) {
                continue;
            }
            total += score;
            if (random.nextDouble() * total < score) {
                chosen = c;
            }
        }
        return chosen != null ? chosen : frontier.iterator().next();
    }

    private ChunkCoord pickWeighted(GridBounds bounds, double[] weight) {
        double total = 0;
        ChunkCoord chosen = null;
        for (int z = 0; z < bounds.height(); z++) {
            for (int x = 0; x < bounds.width(); x++) {
                double w = weight[bounds.index(x, z)];
                if (w <= 0) {
                    continue;
                }
                total += w;
                if (random.nextDouble() * total < w) {
                    chosen = new ChunkCoord(bounds.minX() + x, bounds.minZ() + z);
                }
            }
        }
        return chosen;
    }

    private void pushNeighbours(ChunkCoord cell, GridBounds bounds, Set<ChunkCoord> selected,
                                 Set<ChunkCoord> frontier, Map<ChunkCoord, Integer> adjCount) {
        for (ChunkCoord n : orthNeighbours(cell)) {
            if (!bounds.contains(n.x(), n.z()) || selected.contains(n)) {
                continue;
            }
            adjCount.merge(n, 1, Integer::sum);
            frontier.add(n);
        }
    }

    private List<ChunkCoord> orthNeighbours(ChunkCoord c) {
        return List.of(
                new ChunkCoord(c.x() + 1, c.z()), new ChunkCoord(c.x() - 1, c.z()),
                new ChunkCoord(c.x(), c.z() + 1), new ChunkCoord(c.x(), c.z() - 1));
    }

    private List<ChunkCoord> eightNeighbours(ChunkCoord c) {
        List<ChunkCoord> list = new ArrayList<>(8);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx != 0 || dz != 0) {
                    list.add(new ChunkCoord(c.x() + dx, c.z() + dz));
                }
            }
        }
        return list;
    }

    private boolean isValidCandidate(ChunkCoord c, GridBounds bounds, double[] weight, boolean[] occupied,
                                      RegionSettings settings, GeneratedCheck generated,
                                      Map<Long, Boolean> generatedCache) {
        int idx = bounds.index(bounds.localX(c.x()), bounds.localZ(c.z()));
        if (weight[idx] <= 0) {
            return false;
        }
        if (!settings.allowClaimed() && occupied[idx]) {
            return false;
        }
        if (!settings.requireGenerated()) {
            return true;
        }
        return generatedCache.computeIfAbsent(c.packed(), k -> generated.isGenerated(c.x(), c.z()));
    }

    private Map<ChunkCoord, Integer> computeInfection(Set<ChunkCoord> core, GridBounds bounds, double[] weight,
                                                        boolean[] occupied, RegionSettings settings,
                                                        GeneratedCheck generated, Map<Long, Boolean> generatedCache) {
        Map<ChunkCoord, Integer> infected = new LinkedHashMap<>();
        if (settings.infection().rings() <= 0) {
            return infected;
        }
        Set<ChunkCoord> frontier = new LinkedHashSet<>(core);
        Set<ChunkCoord> visited = new HashSet<>(core);
        for (int ring = 1; ring <= settings.infection().rings(); ring++) {
            Set<ChunkCoord> next = new LinkedHashSet<>();
            for (ChunkCoord c : frontier) {
                for (ChunkCoord n : eightNeighbours(c)) {
                    if (!bounds.contains(n.x(), n.z()) || visited.contains(n)) {
                        continue;
                    }
                    visited.add(n);
                    if (!isValidCandidate(n, bounds, weight, occupied, settings, generated, generatedCache)) {
                        continue;
                    }
                    infected.put(n, ring);
                    next.add(n);
                }
            }
            frontier = next;
        }
        return infected;
    }

    private double weight(double dist, WeightSettings w) {
        if (dist < w.minDistanceChunks()) {
            return 0;
        }
        double t = (Math.min(dist, w.capDistanceChunks()) - w.minDistanceChunks())
                / (w.capDistanceChunks() - w.minDistanceChunks());
        return w.baseWeight() + (1 - w.baseWeight()) * Math.pow(t, w.exponent());
    }
}
