package cn.ykntd.arena;

import cn.ykntd.arena.FloatingIslandGeometry.Site;
import cn.ykntd.model.Lane;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Random;

/**
 * Generates the permanent-night floating-island arena.
 *
 * <p>The playable coordinates are intentionally flat so Paper pathfinding can
 * reliably follow the four axes. All height and colour variation is confined
 * to the floor, the bridge edges, or scenery outside the three-block-wide
 * navigation corridor.</p>
 */
public final class FloatingIslandArenaGenerator extends ChunkGenerator {
    private static final int PORTAL_DISTANCE = 78;
    private static final BiomeProvider VOID_BIOME = new BiomeProvider() {
        @Override
        public @NotNull Biome getBiome(@NotNull WorldInfo worldInfo, int x, int y, int z) {
            return Biome.THE_VOID;
        }

        @Override
        public @NotNull List<Biome> getBiomes(@NotNull WorldInfo worldInfo) {
            return List.of(Biome.THE_VOID);
        }
    };

    private final int floorY;

    public FloatingIslandArenaGenerator(int floorY) {
        this.floorY = floorY;
    }

    @Override
    public void generateNoise(@NotNull WorldInfo worldInfo, @NotNull Random random,
                              int chunkX, int chunkZ, @NotNull ChunkData data) {
        int startX = chunkX << 4;
        int startZ = chunkZ << 4;
        long seed = worldInfo.getSeed();
        for (int localX = 0; localX < 16; localX++) {
            int worldX = startX + localX;
            for (int localZ = 0; localZ < 16; localZ++) {
                int worldZ = startZ + localZ;
                Site site = FloatingIslandGeometry.siteAt(worldX, worldZ);
                if (site == Site.VOID) continue;
                generateFoundation(data, localX, localZ, worldX, worldZ, site, seed);
                generateDecoration(data, localX, localZ, worldX, worldZ, site, seed);
            }
        }
    }

    private void generateFoundation(ChunkData data, int localX, int localZ,
                                    int worldX, int worldZ, Site site, long seed) {
        long terrainHash = hash(worldX, worldZ, seed);
        int depth = FloatingIslandGeometry.depthAt(worldX, worldZ, site);
        if (site != Site.BRIDGE && site != Site.WAYSTATION) {
            depth += (int) ((terrainHash >>> 11) & 1L);
        }

        for (int offset = depth; offset >= 2; offset--) {
            data.setBlock(localX, floorY - offset, localZ,
                    foundationMaterial(offset, depth, terrainHash));
        }
        data.setBlock(localX, floorY - 1, localZ,
                (terrainHash & 7L) == 0L ? Material.POLISHED_DEEPSLATE : Material.DEEPSLATE_TILES);
        data.setBlock(localX, floorY, localZ,
                surfaceMaterial(worldX, worldZ, site, terrainHash));

        if (site != Site.BRIDGE && site != Site.WAYSTATION
                && depth <= 6 && Math.floorMod(terrainHash, 29L) == 0L) {
            int chainLength = 2 + (int) ((terrainHash >>> 17) & 3L);
            for (int length = 1; length <= chainLength; length++) {
                data.setBlock(localX, floorY - depth - length, localZ, Material.IRON_BARS);
            }
        }
    }

    private static Material foundationMaterial(int offset, int depth, long terrainHash) {
        long vein = terrainHash + offset * 31L;
        if (offset == depth) return Material.BLACKSTONE;
        if (Math.floorMod(vein, 67L) == 0L) return Material.CRYING_OBSIDIAN;
        if (Math.floorMod(vein, 43L) == 0L) return Material.GILDED_BLACKSTONE;
        if (offset >= 7) return Material.POLISHED_BASALT;
        return offset <= 3 ? Material.DEEPSLATE_BRICKS : Material.BLACKSTONE;
    }

    private Material surfaceMaterial(int x, int z, Site site, long terrainHash) {
        return switch (site) {
            case CENTRAL -> centralSurface(x, z, terrainHash);
            case BRIDGE -> bridgeSurface(x, z, terrainHash);
            case WAYSTATION -> waystationSurface(x, z, terrainHash);
            case WARD -> wardSurface(x, z, terrainHash);
            case OUTER -> outerSurface(x, z, terrainHash);
            case SATELLITE -> satelliteSurface(x, z, terrainHash);
            case VOID -> Material.AIR;
        };
    }

    private Material centralSurface(int x, int z, long terrainHash) {
        if (Math.abs(x) <= 1 && Math.abs(z) <= 1) return Material.NETHERITE_BLOCK;

        Lane lane = FloatingIslandGeometry.laneAt(x, z);
        int forward = FloatingIslandGeometry.forward(lane, x, z);
        int side = FloatingIslandGeometry.perpendicular(lane, x, z);
        if (forward >= 3 && Math.abs(side) <= 1) {
            return Math.floorMod(forward, 7) == 0 ? laneLight(lane) : laneAccent(lane);
        }

        long radiusSquared = squared(x, z);
        if (nearRing(radiusSquared, 7)) {
            return ((x + z) & 1) == 0
                    ? Material.CHISELED_POLISHED_BLACKSTONE : Material.POLISHED_BLACKSTONE_BRICKS;
        }
        if (nearRing(radiusSquared, 15)) {
            return Math.floorMod(x + z, 3) == 0 ? laneAccent(lane) : Material.AMETHYST_BLOCK;
        }
        if (nearRing(radiusSquared, 22)) {
            return ((x - z) & 1) == 0 ? Material.CRYING_OBSIDIAN : Material.POLISHED_BASALT;
        }
        if (Math.abs(x) == Math.abs(z) && Math.abs(x) >= 8 && Math.abs(x) <= 19
                && Math.floorMod(Math.abs(x), 3) == 0) {
            return Material.AMETHYST_BLOCK;
        }
        if (Math.floorMod(terrainHash, 37L) == 0L) return Material.SCULK;
        if (Math.floorMod(terrainHash, 23L) == 0L) return Material.GILDED_BLACKSTONE;
        return Material.POLISHED_BLACKSTONE_BRICKS;
    }

    private Material bridgeSurface(int x, int z, long terrainHash) {
        Lane lane = FloatingIslandGeometry.laneAt(x, z);
        int forward = FloatingIslandGeometry.forward(lane, x, z);
        int side = FloatingIslandGeometry.perpendicular(lane, x, z);
        int edge = Math.abs(side);
        if (edge == FloatingIslandGeometry.BRIDGE_HALF_WIDTH) return Material.POLISHED_BLACKSTONE_BRICKS;
        if (edge == FloatingIslandGeometry.BRIDGE_HALF_WIDTH - 1) return Material.CHISELED_DEEPSLATE;
        if (Math.floorMod(forward, 7) == 0) {
            if (edge == 3) return laneLight(lane);
            return edge <= 2 ? laneSecondary(lane) : Material.POLISHED_BASALT;
        }
        if (side == 0) return laneAccent(lane);
        if (Math.floorMod(terrainHash, 29L) == 0L) return Material.CRACKED_DEEPSLATE_TILES;
        return Material.DEEPSLATE_TILES;
    }

    private Material waystationSurface(int x, int z, long terrainHash) {
        Lane lane = FloatingIslandGeometry.laneAt(x, z);
        int forward = FloatingIslandGeometry.forward(lane, x, z);
        int side = FloatingIslandGeometry.perpendicular(lane, x, z);
        int delta = forward - FloatingIslandGeometry.nearestWaypoint(forward);
        if (Math.abs(side) == FloatingIslandGeometry.WAYSTATION_HALF_WIDTH
                || Math.abs(delta) == FloatingIslandGeometry.WAYSTATION_HALF_LENGTH) {
            return ((side + delta) & 1) == 0 ? Material.POLISHED_BASALT : Material.GILDED_BLACKSTONE;
        }
        if (delta == 0 && Math.abs(side) == 5) return laneLight(lane);
        if (side == 0 || Math.abs(side) == Math.abs(delta) + 2) return laneAccent(lane);
        if (Math.floorMod(terrainHash, 11L) == 0L) return laneSecondary(lane);
        return Material.POLISHED_BLACKSTONE_BRICKS;
    }

    private Material wardSurface(int x, int z, long terrainHash) {
        Lane lane = FloatingIslandGeometry.laneAt(x, z);
        int localForward = FloatingIslandGeometry.forward(lane, x, z)
                - DedicatedArenaLayout.WARD_DISTANCE;
        int side = FloatingIslandGeometry.perpendicular(lane, x, z);
        long radiusSquared = squared(localForward, side);
        if (localForward == 0 && side == 0) return laneAccent(lane);
        if (nearRing(radiusSquared, 3)) {
            return ((localForward + side) & 1) == 0 ? Material.CALCITE : laneAccent(lane);
        }
        if (nearRing(radiusSquared, 6)) {
            return ((localForward - side) & 1) == 0 ? laneSecondary(lane) : Material.CRYING_OBSIDIAN;
        }
        if (localForward == 0 || side == 0) return Material.CHISELED_POLISHED_BLACKSTONE;
        if (Math.floorMod(terrainHash, 13L) == 0L) return Material.GILDED_BLACKSTONE;
        return Material.POLISHED_BLACKSTONE_BRICKS;
    }

    private Material outerSurface(int x, int z, long terrainHash) {
        Lane lane = FloatingIslandGeometry.laneAt(x, z);
        int localForward = FloatingIslandGeometry.forward(lane, x, z)
                - DedicatedArenaLayout.SPAWN_DISTANCE;
        int side = FloatingIslandGeometry.perpendicular(lane, x, z);
        long radiusSquared = squared(localForward, side);
        if (Math.abs(side) <= 1) {
            return Math.floorMod(localForward, 6) == 0 ? laneLight(lane) : laneAccent(lane);
        }
        if (nearRing(radiusSquared, 6)) {
            return ((localForward + side) & 1) == 0 ? laneSecondary(lane) : Material.CHISELED_DEEPSLATE;
        }
        if (nearRing(radiusSquared, 13)) {
            return ((localForward - side) & 1) == 0 ? Material.CRYING_OBSIDIAN : Material.POLISHED_BASALT;
        }
        if (localForward == 0 && Math.abs(side) == 5) return laneLight(lane);
        if (Math.floorMod(terrainHash, 31L) == 0L) return Material.SCULK;
        return Material.POLISHED_BLACKSTONE_BRICKS;
    }

    private Material satelliteSurface(int x, int z, long terrainHash) {
        int centerX = x < 0 ? -FloatingIslandGeometry.SATELLITE_OFFSET
                : FloatingIslandGeometry.SATELLITE_OFFSET;
        int centerZ = z < 0 ? -FloatingIslandGeometry.SATELLITE_OFFSET
                : FloatingIslandGeometry.SATELLITE_OFFSET;
        int localX = x - centerX;
        int localZ = z - centerZ;
        if (nearRing(squared(localX, localZ), 4)) return Material.CALCITE;
        if (localX == 0 || localZ == 0) return Material.AMETHYST_BLOCK;
        if (Math.floorMod(terrainHash, 5L) == 0L) return Material.CRYING_OBSIDIAN;
        return Material.POLISHED_BLACKSTONE_BRICKS;
    }

    private void generateDecoration(ChunkData data, int localX, int localZ,
                                    int x, int z, Site site, long seed) {
        switch (site) {
            case CENTRAL -> decorateCentral(data, localX, localZ, x, z, seed);
            case BRIDGE -> decorateBridge(data, localX, localZ, x, z);
            case WAYSTATION -> decorateWaystation(data, localX, localZ, x, z);
            case WARD -> decorateWard(data, localX, localZ, x, z);
            case OUTER -> decorateOuter(data, localX, localZ, x, z, seed);
            case SATELLITE -> decorateSatellite(data, localX, localZ, x, z);
            case VOID -> {
                // No terrain or scenery in the void.
            }
        }
    }

    private void decorateCentral(ChunkData data, int localX, int localZ,
                                 int x, int z, long seed) {
        if (Math.abs(x - DedicatedArenaLayout.LOBBY_X) <= 2
                && Math.abs(z - DedicatedArenaLayout.LOBBY_Z) <= 2) {
            return;
        }
        if (x == 0 && z == 0) {
            data.setBlock(localX, floorY + 1, localZ, Material.BEACON);
            return;
        }
        if (Math.abs(x) == 5 && Math.abs(z) == 5) {
            data.setBlock(localX, floorY + 1, localZ, Material.CRYING_OBSIDIAN);
            data.setBlock(localX, floorY + 2, localZ, Material.AMETHYST_BLOCK);
            data.setBlock(localX, floorY + 3, localZ, Material.AMETHYST_CLUSTER);
            return;
        }

        int absoluteX = Math.abs(x);
        int absoluteZ = Math.abs(z);
        if ((absoluteX == 18 && absoluteZ == 8) || (absoluteX == 8 && absoluteZ == 18)) {
            Lane lane = FloatingIslandGeometry.laneAt(x, z);
            data.setBlock(localX, floorY + 1, localZ, Material.CHISELED_POLISHED_BLACKSTONE);
            data.setBlock(localX, floorY + 2, localZ, Material.POLISHED_BASALT);
            data.setBlock(localX, floorY + 3, localZ, laneAccent(lane));
            data.setBlock(localX, floorY + 4, localZ, Material.CRYING_OBSIDIAN);
            data.setBlock(localX, floorY + 5, localZ, Material.SOUL_LANTERN);
            return;
        }

        long radiusSquared = squared(x, z);
        Lane lane = FloatingIslandGeometry.laneAt(x, z);
        int side = FloatingIslandGeometry.perpendicular(lane, x, z);
        if (radiusSquared >= squared(23) && Math.abs(side) > 5
                && Math.floorMod(hash(x, z, seed), 4L) != 0L) {
            data.setBlock(localX, floorY + 1, localZ, Material.POLISHED_BLACKSTONE_BRICK_WALL);
            if (Math.floorMod(absoluteX * 3 + absoluteZ * 5, 11) == 0) {
                data.setBlock(localX, floorY + 2, localZ, laneAccent(lane));
                data.setBlock(localX, floorY + 3, localZ, Material.SOUL_LANTERN);
            }
        }
    }

    private void decorateBridge(ChunkData data, int localX, int localZ, int x, int z) {
        Lane lane = FloatingIslandGeometry.laneAt(x, z);
        int forward = FloatingIslandGeometry.forward(lane, x, z);
        int side = FloatingIslandGeometry.perpendicular(lane, x, z);
        if (Math.abs(side) != FloatingIslandGeometry.BRIDGE_HALF_WIDTH) return;

        if (Math.floorMod(forward, 12) == 0) {
            data.setBlock(localX, floorY + 1, localZ, Material.POLISHED_BLACKSTONE_BRICK_WALL);
            data.setBlock(localX, floorY + 2, localZ, laneAccent(lane));
            data.setBlock(localX, floorY + 3, localZ, laneLight(lane));
        } else if (Math.floorMod(forward, 4) != 0) {
            data.setBlock(localX, floorY + 1, localZ, Material.POLISHED_BLACKSTONE_BRICK_WALL);
        }
    }

    private void decorateWaystation(ChunkData data, int localX, int localZ, int x, int z) {
        Lane lane = FloatingIslandGeometry.laneAt(x, z);
        int forward = FloatingIslandGeometry.forward(lane, x, z);
        int side = FloatingIslandGeometry.perpendicular(lane, x, z);
        int waypoint = FloatingIslandGeometry.nearestWaypoint(forward);
        if (forward == waypoint && Math.abs(side) == 6) {
            data.setBlock(localX, floorY + 1, localZ, Material.CHISELED_POLISHED_BLACKSTONE);
            data.setBlock(localX, floorY + 2, localZ, laneSecondary(lane));
            data.setBlock(localX, floorY + 3, localZ, Material.POLISHED_BLACKSTONE_BRICK_WALL);
            data.setBlock(localX, floorY + 4, localZ, laneLight(lane));
        } else if (Math.abs(side) == FloatingIslandGeometry.WAYSTATION_HALF_WIDTH
                && Math.abs(forward - waypoint) <= 1) {
            data.setBlock(localX, floorY + 1, localZ, Material.POLISHED_BLACKSTONE_BRICK_WALL);
        }
    }

    private void decorateWard(ChunkData data, int localX, int localZ, int x, int z) {
        Lane lane = FloatingIslandGeometry.laneAt(x, z);
        int localForward = FloatingIslandGeometry.forward(lane, x, z)
                - DedicatedArenaLayout.WARD_DISTANCE;
        int side = FloatingIslandGeometry.perpendicular(lane, x, z);
        if (localForward == 0 && side == 0) {
            data.setBlock(localX, floorY + 1, localZ, Material.AMETHYST_BLOCK);
            return;
        }
        if (Math.abs(localForward) == 3 && Math.abs(side) == 5) {
            data.setBlock(localX, floorY + 1, localZ, Material.CHISELED_POLISHED_BLACKSTONE);
            data.setBlock(localX, floorY + 2, localZ, Material.POLISHED_BASALT);
            data.setBlock(localX, floorY + 3, localZ, laneSecondary(lane));
            data.setBlock(localX, floorY + 4, localZ, Material.CRYING_OBSIDIAN);
            data.setBlock(localX, floorY + 5, localZ, laneLight(lane));
        }
    }

    private void decorateOuter(ChunkData data, int localX, int localZ,
                               int x, int z, long seed) {
        Lane lane = FloatingIslandGeometry.laneAt(x, z);
        int forward = FloatingIslandGeometry.forward(lane, x, z);
        int localForward = forward - DedicatedArenaLayout.SPAWN_DISTANCE;
        int side = FloatingIslandGeometry.perpendicular(lane, x, z);

        if (Math.abs(localForward) <= 2 && Math.abs(side) <= 2) return;

        if (forward == PORTAL_DISTANCE) {
            if (Math.abs(side) == 6) {
                for (int height = 1; height <= 6; height++) {
                    Material material = height == 3 || height == 5
                            ? laneAccent(lane) : Material.CRYING_OBSIDIAN;
                    data.setBlock(localX, floorY + height, localZ, material);
                }
            }
            if (Math.abs(side) <= 6) {
                data.setBlock(localX, floorY + 7, localZ,
                        Math.abs(side) <= 1 ? laneLight(lane) : Material.CRYING_OBSIDIAN);
            }
            if (Math.abs(side) <= 4) {
                for (int height = 2; height <= 5; height++) {
                    data.setBlock(localX, floorY + height, localZ, laneGlass(lane));
                }
            }
            return;
        }

        if (localForward == 0 && Math.abs(side) == 11) {
            data.setBlock(localX, floorY + 1, localZ, Material.CHISELED_POLISHED_BLACKSTONE);
            data.setBlock(localX, floorY + 2, localZ, Material.POLISHED_BASALT);
            data.setBlock(localX, floorY + 3, localZ, laneAccent(lane));
            data.setBlock(localX, floorY + 4, localZ, laneLight(lane));
            return;
        }

        long radiusSquared = squared(localForward, side);
        boolean frontOpening = localForward <= -10 && Math.abs(side) <= 5;
        if (!frontOpening && radiusSquared >= squared(14)
                && Math.floorMod(hash(x, z, seed), 3L) != 0L) {
            data.setBlock(localX, floorY + 1, localZ, Material.DEEPSLATE_TILE_WALL);
        }
    }

    private void decorateSatellite(ChunkData data, int localX, int localZ, int x, int z) {
        int centerX = x < 0 ? -FloatingIslandGeometry.SATELLITE_OFFSET
                : FloatingIslandGeometry.SATELLITE_OFFSET;
        int centerZ = z < 0 ? -FloatingIslandGeometry.SATELLITE_OFFSET
                : FloatingIslandGeometry.SATELLITE_OFFSET;
        int relativeX = x - centerX;
        int relativeZ = z - centerZ;
        if (relativeX == 0 && relativeZ == 0) {
            data.setBlock(localX, floorY + 1, localZ, Material.CRYING_OBSIDIAN);
            data.setBlock(localX, floorY + 2, localZ, Material.CALCITE);
            data.setBlock(localX, floorY + 3, localZ, Material.AMETHYST_BLOCK);
            data.setBlock(localX, floorY + 4, localZ, Material.AMETHYST_BLOCK);
            data.setBlock(localX, floorY + 5, localZ, Material.AMETHYST_BLOCK);
            data.setBlock(localX, floorY + 6, localZ, Material.AMETHYST_CLUSTER);
            return;
        }
        if ((Math.abs(relativeX) == 3 && relativeZ == 0)
                || (Math.abs(relativeZ) == 3 && relativeX == 0)) {
            data.setBlock(localX, floorY + 1, localZ, Material.AMETHYST_BLOCK);
            data.setBlock(localX, floorY + 2, localZ, Material.END_ROD);
        }
    }

    private static Material laneAccent(Lane lane) {
        return switch (lane) {
            case NORTH -> Material.DARK_PRISMARINE;
            case EAST -> Material.WAXED_CUT_COPPER;
            case SOUTH -> Material.MOSSY_STONE_BRICKS;
            case WEST -> Material.PURPUR_PILLAR;
        };
    }

    private static Material laneSecondary(Lane lane) {
        return switch (lane) {
            case NORTH -> Material.PRISMARINE_BRICKS;
            case EAST -> Material.WAXED_EXPOSED_CUT_COPPER;
            case SOUTH -> Material.TUFF_BRICKS;
            case WEST -> Material.AMETHYST_BLOCK;
        };
    }

    private static Material laneLight(Lane lane) {
        return switch (lane) {
            case NORTH -> Material.SEA_LANTERN;
            case EAST -> Material.OCHRE_FROGLIGHT;
            case SOUTH -> Material.VERDANT_FROGLIGHT;
            case WEST -> Material.PEARLESCENT_FROGLIGHT;
        };
    }

    private static Material laneGlass(Lane lane) {
        return switch (lane) {
            case NORTH -> Material.CYAN_STAINED_GLASS;
            case EAST -> Material.ORANGE_STAINED_GLASS;
            case SOUTH -> Material.LIME_STAINED_GLASS;
            case WEST -> Material.MAGENTA_STAINED_GLASS;
        };
    }

    private static boolean nearRing(long distanceSquared, int radius) {
        long inner = (long) (radius - 1) * (radius - 1);
        long outer = (long) (radius + 1) * (radius + 1);
        return distanceSquared >= inner && distanceSquared <= outer;
    }

    private static long squared(int value) {
        return (long) value * value;
    }

    private static long squared(int first, int second) {
        return (long) first * first + (long) second * second;
    }

    private static long hash(int x, int z, long seed) {
        long value = x * 341873128712L + z * 132897987541L + seed;
        value ^= value >>> 29;
        value *= 0x9E3779B97F4A7C15L;
        return value ^ value >>> 32;
    }

    @Override
    public int getBaseHeight(@NotNull WorldInfo worldInfo, @NotNull Random random,
                             int x, int z, @NotNull HeightMap heightMap) {
        return FloatingIslandGeometry.siteAt(x, z) == Site.VOID
                ? worldInfo.getMinHeight() : floorY + 1;
    }

    @Override
    public @NotNull Location getFixedSpawnLocation(@NotNull World world, @NotNull Random random) {
        return new Location(world, DedicatedArenaLayout.LOBBY_X + 0.5, floorY + 1.0,
                DedicatedArenaLayout.LOBBY_Z + 0.5, 135.0f, 0.0f);
    }

    @Override
    public @NotNull BiomeProvider getDefaultBiomeProvider(@NotNull WorldInfo worldInfo) {
        return VOID_BIOME;
    }

    @Override
    public boolean canSpawn(@NotNull World world, int x, int z) {
        return FloatingIslandGeometry.siteAt(x, z) != Site.VOID;
    }

    @Override public boolean shouldGenerateNoise() { return false; }
    @Override public boolean shouldGenerateSurface() { return false; }
    @Override public boolean shouldGenerateCaves() { return false; }
    @Override public boolean shouldGenerateDecorations() { return false; }
    @Override public boolean shouldGenerateMobs() { return false; }
    @Override public boolean shouldGenerateStructures() { return false; }
}
