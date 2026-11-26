package com.mercuriusxeno.goo.lunar;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.synth.Noise;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The frozen pools in the floors of lunar's deep and giant craters in the shadowed basins: each such crater's floor
 * height is read from the generator's own ground at points across its floor, and crater ice fills every column inside
 * its rim crest whose ground lies under a level just over that floor, up to that flat level, as frozen water collects
 * in low ground. The level never stands over the lowest ground just past the crest, so wherever the pool's edge meets
 * the crest it meets ground, and a hole a tube or cave opened in the floor is capped with ice rather than left open:
 * the shoreline follows the ground's own contour, never a circle cut, and the ice never climbs a central peak or a
 * terraced wall.
 * decision lunar-ground-gains-meteoric-iron-impact-glass-breccia-and-crater-ice
 * decision moon-is-288-tall-half-of-today
 * decision no-two-lunar-features-look-alike
 * decision lunar-surface-has-maria-highlands-shadowed-basins-and-eldritch-ground
 */
public final class CraterPoolsFeature implements Feature {

    /** The feature type's codec, which reads no settings. */
    public static final MapCodec<CraterPoolsFeature> CODEC = MapCodec.unit(CraterPoolsFeature::new);

    /** A small deep crater's pool stands this far over its floor, and a giant crater's this far. */
    static final int SMALL_POOL_DEPTH = 1;
    static final int GIANT_POOL_DEPTH = 2;
    /** A small crater's floor is read at its centre and this far out, in radii; a giant crater's at this reach. */
    static final double SMALL_FLOOR_SAMPLE_REACH = 0.1;
    static final double GIANT_FLOOR_SAMPLE_REACH = 0.3;
    /** How many points round the floor are read, past the centre. */
    static final int FLOOR_SAMPLES = 8;
    /** A column whose ground lies further under the floor than this is a hole a tube or cave opened, capped this deep. */
    static final int HOLE_DEPTH = 1;
    /** The crest's ring is read from the crest out this far, past any column a pool's edge column borders. */
    private static final double CREST_RING_WIDTH = 1.5;
    /** A pool's crater reads its biome at this height, over the warrens' band. */
    private static final int SURFACE_PROBE_Y = 64;
    /** The pools' one home. */
    private static final ResourceKey<Biome> SHADOWED_BASINS = ResourceKey.create(Registries.BIOME,
            Identifier.fromNamespaceAndPath(Goo.MODID, "lunar_shadowed_basins"));
    private static final int CHUNK_SIDE = 16;
    /** A chunk reads the giant craters of its own cell and the cells one step round it. */
    private static final int NEIGHBOUR_REACH = 1;
    private static final double FULL_TURN = 2.0 * Math.PI;
    private static final int POOL_CACHE_SIZE = 256;
    private static final int PLACE_FLAGS = Block.UPDATE_CLIENTS;
    /** Each crater's pool level, kept by the noise it was rolled from and its cell, once read. */
    private static final Map<PoolKey, Integer> POOL_LEVELS = Collections.synchronizedMap(
            new LinkedHashMap<>(POOL_CACHE_SIZE, 0.75F, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<PoolKey, Integer> eldest) {
                    return size() > POOL_CACHE_SIZE;
                }
            });

    /** A crater's pool, keyed by the crater noise it was rolled from, its scale and its cell. */
    private record PoolKey(Noise noise, boolean giant, int cellX, int cellZ) {
    }

    /** One crater with a pool: its centre, its radius and its pool's flat level. */
    private record Pool(double centerX, double centerZ, double radius, int level) {
    }

    /**
     * The crater pools feature, which takes no configuration.
     */
    public CraterPoolsFeature() {
    }

    /**
     * The middle of a set of floor heights, so one height a hole opened reads no differently from the rest.
     *
     * @param heights the heights
     * @return their median
     */
    static int median(int[] heights) {
        int[] sorted = heights.clone();
        Arrays.sort(sorted);
        return sorted[sorted.length >> 1];
    }

    @Override
    public MapCodec<CraterPoolsFeature> codec() {
        return CODEC;
    }

    @Override
    public boolean place(WorldGenLevel level, ChunkGenerator chunkGenerator, RandomSource random, BlockPos origin) {
        ServerLevel server = level.getLevel();
        RandomState randomState = server.getChunkSource().randomState();
        Noise noise = randomState.getOrCreateNoise(CraterField.NOISE);
        int minX = Math.floorDiv(origin.getX(), CHUNK_SIDE) * CHUNK_SIDE;
        int minZ = Math.floorDiv(origin.getZ(), CHUNK_SIDE) * CHUNK_SIDE;
        List<Pool> pools = poolsNear(chunkGenerator, server, randomState, noise, minX, minZ);
        if (pools.isEmpty()) {
            return false;
        }
        BlockState ice = GooBlocks.CRATER_ICE.get().defaultBlockState();
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        boolean froze = false;
        for (int x = minX; x < minX + CHUNK_SIDE; x++) {
            for (int z = minZ; z < minZ + CHUNK_SIDE; z++) {
                int ground = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
                int poolLevel = poolLevelOver(pools, x, z, ground);
                for (int y = iceFloor(ground, poolLevel); y <= poolLevel; y++) {
                    level.setBlock(at.set(x, y, z), ice, PLACE_FLAGS);
                    froze = true;
                }
            }
        }
        return froze;
    }

    /**
     * The highest pool level over a column's ground among the pools whose rim crest it lies inside.
     *
     * @param pools  the pools reaching the column's chunk
     * @param x      the column's x
     * @param z      the column's z
     * @param ground the top of the column's ground
     * @return the highest level over it, or {@link Integer#MIN_VALUE} where no pool reaches over it
     */
    private static int poolLevelOver(List<Pool> pools, int x, int z, int ground) {
        int highest = Integer.MIN_VALUE;
        for (Pool pool : pools) {
            boolean inside = Math.hypot(x - pool.centerX(), z - pool.centerZ()) < pool.radius();
            if (inside && ground <= pool.level()) {
                highest = Math.max(highest, pool.level());
            }
        }
        return highest;
    }

    /**
     * The lowest a column's ice starts under a pool's level: on the column's ground, or capping a hole a tube or cave
     * opened a little under the level.
     *
     * @param ground    the top of the column's ground
     * @param poolLevel the pool's level over it
     * @return the lowest ice block's height
     */
    static int iceFloor(int ground, int poolLevel) {
        return Math.max(ground, poolLevel - HOLE_DEPTH - GIANT_POOL_DEPTH);
    }

    /**
     * Whether a crater's centre stands in the shadowed basins, the pools' one home.
     *
     * @param generator   the level's chunk generator, whose biome source places the biomes
     * @param randomState the level's random state, seeding the climate the biome source reads
     * @param centerX     the crater's centre x
     * @param centerZ     the crater's centre z
     * @return true where the centre stands in the shadowed basins
     */
    private static boolean centredInBasins(ChunkGenerator generator, RandomState randomState, double centerX,
            double centerZ) {
        return generator.getBiomeSource().createResolver(randomState.createClimateSampler(SamplerContext.EMPTY_UNCACHED))
                .getNoiseBiome(QuartPos.fromBlock((int) Math.floor(centerX)), QuartPos.fromBlock(SURFACE_PROBE_Y),
                        QuartPos.fromBlock((int) Math.floor(centerZ))).is(SHADOWED_BASINS);
    }

    private static List<Pool> poolsNear(ChunkGenerator generator, ServerLevel server, RandomState randomState,
            Noise noise, int minX, int minZ) {
        List<Pool> pools = new ArrayList<>();
        CraterField craters = CraterField.seeded(noise, randomState);
        int smallReach = (int) Math.ceil(CraterField.MIN_RADIUS + CraterField.RADIUS_SPREAD);
        for (int cellX = Math.floorDiv(minX - smallReach, CraterField.CELL);
                cellX <= Math.floorDiv(minX + CHUNK_SIDE + smallReach, CraterField.CELL); cellX++) {
            for (int cellZ = Math.floorDiv(minZ - smallReach, CraterField.CELL);
                    cellZ <= Math.floorDiv(minZ + CHUNK_SIDE + smallReach, CraterField.CELL); cellZ++) {
                int cx = cellX;
                int cz = cellZ;
                craters.craterInCell(cx, cz).filter(crater -> crater.radius() >= CraterField.DEEP_CRATER_RADIUS)
                        .filter(crater -> centredInBasins(generator, randomState, crater.centerX(), crater.centerZ()))
                        .ifPresent(crater -> pools.add(new Pool(crater.centerX(), crater.centerZ(), crater.radius(),
                                poolLevel(new PoolKey(noise, false, cx, cz), generator, server, randomState,
                                        crater.centerX(), crater.centerZ(), crater.radius(),
                                        crater.radius() * SMALL_FLOOR_SAMPLE_REACH, SMALL_POOL_DEPTH))));
            }
        }
        GiantCraterField giants = GiantCraterField.seeded(noise, randomState);
        int cellX = Math.floorDiv(minX, GiantCraterField.CELL);
        int cellZ = Math.floorDiv(minZ, GiantCraterField.CELL);
        for (int dx = -NEIGHBOUR_REACH; dx <= NEIGHBOUR_REACH; dx++) {
            for (int dz = -NEIGHBOUR_REACH; dz <= NEIGHBOUR_REACH; dz++) {
                int cx = cellX + dx;
                int cz = cellZ + dz;
                giants.craterInCell(cx, cz)
                        .filter(crater -> centredInBasins(generator, randomState, crater.centerX(), crater.centerZ()))
                        .ifPresent(crater -> pools.add(new Pool(crater.centerX(), crater.centerZ(), crater.radius(),
                                poolLevel(new PoolKey(noise, true, cx, cz), generator, server, randomState,
                                        crater.centerX(), crater.centerZ(), crater.radius(),
                                        crater.radius() * GIANT_FLOOR_SAMPLE_REACH, GIANT_POOL_DEPTH))));
            }
        }
        return pools;
    }

    /**
     * A crater's pool level: the median of the generator's ground across its floor ring, plus the pool's depth, but
     * never over the lowest ground in the ring just past its rim crest, read once per crater and kept.
     *
     * @param key         the crater's key in the kept levels
     * @param generator   the level's chunk generator
     * @param server      the level
     * @param randomState the level's random state
     * @param centerX     the crater's centre x
     * @param centerZ     the crater's centre z
     * @param crest       the crater's rim crest radius, inside which its pool lies
     * @param sampleReach how far from the centre the floor is read, in blocks
     * @param poolDepth   how far the pool stands over the floor
     * @return the pool's level
     */
    private static int poolLevel(PoolKey key, ChunkGenerator generator, ServerLevel server, RandomState randomState,
            double centerX, double centerZ, double crest, double sampleReach, int poolDepth) {
        Integer known = POOL_LEVELS.get(key);
        if (known != null) {
            return known;
        }
        int[] floor = new int[FLOOR_SAMPLES];
        for (int sample = 0; sample < FLOOR_SAMPLES; sample++) {
            double angle = FULL_TURN * sample / FLOOR_SAMPLES;
            BlockPos point = BlockPos.containing(centerX + Math.cos(angle) * sampleReach, 0.0,
                    centerZ + Math.sin(angle) * sampleReach);
            floor[sample] = generator.getBaseHeight(point.getX(), point.getZ(), Heightmap.Types.WORLD_SURFACE_WG,
                    server, randomState) - 1;
        }
        int level = Math.min(median(floor) + poolDepth,
                lowestCrestGround(generator, server, randomState, centerX, centerZ, crest));
        POOL_LEVELS.put(key, level);
        return level;
    }

    /**
     * The lowest ground among the columns from a crater's rim crest out to the ring's width past it: every column a
     * pool's edge column can border outside the crest.
     *
     * @param generator   the level's chunk generator
     * @param server      the level
     * @param randomState the level's random state
     * @param centerX     the crater's centre x
     * @param centerZ     the crater's centre z
     * @param crest       the crater's rim crest radius
     * @return the lowest ground's height
     */
    private static int lowestCrestGround(ChunkGenerator generator, ServerLevel server, RandomState randomState,
            double centerX, double centerZ, double crest) {
        int reach = (int) Math.ceil(crest + CREST_RING_WIDTH);
        int lowest = Integer.MAX_VALUE;
        for (int x = (int) Math.floor(centerX) - reach; x <= (int) Math.ceil(centerX) + reach; x++) {
            for (int z = (int) Math.floor(centerZ) - reach; z <= (int) Math.ceil(centerZ) + reach; z++) {
                double distance = Math.hypot(x - centerX, z - centerZ);
                if (distance >= crest && distance < crest + CREST_RING_WIDTH) {
                    lowest = Math.min(lowest, generator.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, server,
                            randomState) - 1);
                }
            }
        }
        return lowest;
    }
}
