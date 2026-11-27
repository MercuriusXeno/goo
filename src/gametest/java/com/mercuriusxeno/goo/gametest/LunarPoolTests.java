package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.lunar.BasinPools;
import com.mercuriusxeno.goo.lunar.FieldMasks;
import com.mercuriusxeno.goo.lunar.GiantCraterField;
import com.mercuriusxeno.goo.lunar.Lunar;
import com.mercuriusxeno.goo.lunar.LunarRolls;
import com.mercuriusxeno.goo.registry.GooBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.synth.Noise;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Gametests for the ground of lunar's shadowed basins: crater ice pools in the basins' lows, each pool filling the low
 * ground it sits in, so at its edge the ice meets ground standing at or over its surface, never open air a circle cut
 * through, and two pools' outlines differ; and boulders stand in the shadowed basins alone, none in maria, the
 * highlands or eldritch ground.
 * decision lunar-surface-has-maria-highlands-shadowed-basins-and-eldritch-ground
 * decision craters-are-rare-giant-landmarks-only
 * decision no-two-lunar-features-look-alike
 */
@RegistersGameTests
public final class LunarPoolTests {
    private static final String LUNAR_POOLS_FOLLOW_THE_GROUND = "lunar_pools_follow_the_ground";
    private static final String BASINS_HOLD_CRATER_ICE_AND_BOULDERS = "basins_hold_crater_ice_and_boulders";

    /** How many basin pool cells around a spot deep in the shadowed basins a search for pools reaches each way. */
    private static final int CELL_REACH = 32;
    /** A pool's ice is read this far out from its site, in its reaches, past its edge. */
    private static final double READ_REACH = 1.5;
    private static final int POOLS_WANTED = 2;
    /** How many chunks around a spot deep in a biome a search for boulder field spots reaches each way. */
    private static final int CHUNK_REACH = 64;
    /** A biome's boulder samples are this many boulder field spots standing in it. */
    private static final int BOULDER_SAMPLES = 3;
    /** A boulder stands at least this far over the ground the generator laid under it. */
    private static final int BOULDER_RISE = 3;

    private static final String LUNAR_REQUIRED = "The server should hold goo:lunar";
    private static final String NO_SPOT = "No spot within the search lies deep in %s";
    private static final String TOO_FEW_POOLS = "Within %d basin pool cells of %s, %d sites in the shadowed basins "
            + "hold a pool of crater ice, fewer than %d";
    private static final String EDGE_MEETS_AIR = "The pool at %s, its surface at y %d, should meet ground at its edge; "
            + "the ice at %s borders %s at %s";
    private static final String OUTLINES_MATCH = "The pools at %s and %s should differ in outline";
    private static final String NO_BOULDER_SPOTS = "No boulder field spot within %d chunks of %s stands in %s";
    private static final String BASINS_BOULDER = "The boulder field spot %s in the shadowed basins should stand a "
            + "boulder of anorthosite, breccia or basalt %d over the ground, reads %s at y %d over ground at y %d";
    private static final String BOULDER_OUTSIDE_BASINS = "The boulder field spot %s in %s should stand no boulder, "
            + "reads %s at y %d over ground at y %d";

    private LunarPoolTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, LUNAR_POOLS_FOLLOW_THE_GROUND, LunarPoolTests::lunarPoolsFollowTheGround);
        GooTestFunctions.registerTest(registrar, BASINS_HOLD_CRATER_ICE_AND_BOULDERS,
                LunarPoolTests::basinsHoldCraterIceAndBoulders);
    }

    /** One pool as read from the generated world: its site, its surface and its edge offsets. */
    private record Pool(BlockPos centre, int surface, Set<BlockPos> outline) {
    }

    /**
     * Finds two basin pool sites in goo:lunar's shadowed basins whose lows hold crater ice; at every ice block on each
     * pool's surface whose side meets no ice, that side's block is ground, not air; and the two pools' outlines, read
     * from their sites, differ.
     *
     * @param helper the gametest helper
     */
    public static void lunarPoolsFollowTheGround(GameTestHelper helper) {
        ServerLevel lunar = lunar(helper);
        List<Pool> pools = poolsNearBasins(helper, lunar, POOLS_WANTED);
        for (Pool pool : pools) {
            assertEdgeMeetsGround(helper, lunar, pool);
        }
        helper.assertFalse(pools.get(0).outline().equals(pools.get(1).outline()),
                String.format(OUTLINES_MATCH, pools.get(0).centre(), pools.get(1).centre()));
        helper.succeed();
    }

    /**
     * Generates goo:lunar's chunks in the shadowed basins: a basin pool site there holds crater ice, and a boulder
     * field spot there stands a boulder over the ground; then generates boulder field spots in maria, the highlands and
     * eldritch ground, and none of them stands a boulder.
     *
     * @param helper the gametest helper
     */
    public static void basinsHoldCraterIceAndBoulders(GameTestHelper helper) {
        ServerLevel lunar = lunar(helper);
        poolsNearBasins(helper, lunar, 1);
        BlockPos basinsBoulder = boulderSpotsIn(helper, lunar, LunarSurfaceBiomes.SHADOWED_BASINS, 1).get(0);
        int ground = generatorGround(lunar, basinsBoulder);
        BlockPos top = top(lunar, basinsBoulder);
        BlockState stone = lunar.getBlockState(top);
        helper.assertTrue(isBoulderStone(stone) && top.getY() >= ground + BOULDER_RISE,
                String.format(BASINS_BOULDER, basinsBoulder, BOULDER_RISE, stone, top.getY(), ground));
        for (ResourceKey<Biome> biome : List.of(LunarSurfaceBiomes.MARIA, LunarSurfaceBiomes.HIGHLANDS,
                LunarSurfaceBiomes.ELDRITCH_GROUND)) {
            for (BlockPos spot : boulderSpotsIn(helper, lunar, biome, BOULDER_SAMPLES)) {
                int spotGround = generatorGround(lunar, spot);
                BlockPos spotTop = top(lunar, spot);
                BlockState spotStone = lunar.getBlockState(spotTop);
                helper.assertFalse(isBoulderStone(spotStone) && spotTop.getY() >= spotGround + BOULDER_RISE,
                        String.format(BOULDER_OUTSIDE_BASINS, spot, biome.identifier(), spotStone, spotTop.getY(),
                                spotGround));
            }
        }
        helper.succeed();
    }

    private static ServerLevel lunar(GameTestHelper helper) {
        ServerLevel lunar = helper.getLevel().getServer().getLevel(Lunar.LEVEL);
        helper.assertTrue(lunar != null, LUNAR_REQUIRED);
        return lunar;
    }

    /** The first pools of crater ice found at basin pool sites walking out from a spot deep in the basins. */
    private static List<Pool> poolsNearBasins(GameTestHelper helper, ServerLevel lunar, int wanted) {
        BlockPos basins = LunarSurfaceBiomes.spotIn(lunar, LunarSurfaceBiomes.SHADOWED_BASINS);
        helper.assertTrue(basins != null, String.format(NO_SPOT, LunarSurfaceBiomes.SHADOWED_BASINS.identifier()));
        Noise noise = lunar.getChunkSource().randomState().getOrCreateNoise(LunarRolls.CRATER_NOISE);
        BasinPools sites = BasinPools.seeded(noise, lunar.getChunkSource().randomState());
        GiantCraterField giants = GiantCraterField.seeded(noise, lunar.getChunkSource().randomState());
        List<Pool> pools = new ArrayList<>();
        int baseX = Math.floorDiv(basins.getX(), BasinPools.CELL);
        int baseZ = Math.floorDiv(basins.getZ(), BasinPools.CELL);
        for (int dx = -CELL_REACH; dx <= CELL_REACH && pools.size() < wanted; dx++) {
            for (int dz = -CELL_REACH; dz <= CELL_REACH && pools.size() < wanted; dz++) {
                sites.siteInCell(baseX + dx, baseZ + dz).filter(site -> inBasins(lunar, site))
                        .filter(site -> clearOfGiantCraters(giants, site))
                        .flatMap(site -> readPool(lunar, site)).ifPresent(pools::add);
            }
        }
        helper.assertTrue(pools.size() >= wanted, String.format(TOO_FEW_POOLS, CELL_REACH, basins, pools.size(),
                wanted));
        return pools;
    }

    private static boolean inBasins(ServerLevel lunar, BasinPools.Site site) {
        double reach = site.reach() * READ_REACH;
        for (int i = 0; i < 8; i++) {
            double angle = Math.PI * i / 4.0;
            int x = (int) Math.round(site.centerX() + Math.cos(angle) * reach);
            int z = (int) Math.round(site.centerZ() + Math.sin(angle) * reach);
            if (!LunarSurfaceBiomes.surfaceBiomeAt(lunar, x, z).is(LunarSurfaceBiomes.SHADOWED_BASINS)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether a site's read reaches no giant crater, so the ice read there lies in a basin low rather than a giant
     * crater's floor.
     */
    private static boolean clearOfGiantCraters(GiantCraterField giants, BasinPools.Site site) {
        double reach = site.reach() * READ_REACH;
        for (int i = 0; i < 8; i++) {
            double angle = Math.PI * i / 4.0;
            int x = (int) Math.round(site.centerX() + Math.cos(angle) * reach);
            int z = (int) Math.round(site.centerZ() + Math.sin(angle) * reach);
            if (!giants.isOpenGroundAt(x, z)) {
                return false;
            }
        }
        return giants.isOpenGroundAt((int) Math.round(site.centerX()), (int) Math.round(site.centerZ()));
    }

    /** The pool a site holds, read from the generated world, or empty where its low holds no ice. */
    private static Optional<Pool> readPool(ServerLevel lunar, BasinPools.Site site) {
        BlockPos centre = BlockPos.containing(site.centerX(), 0.0, site.centerZ());
        int reach = (int) Math.ceil(site.reach() * READ_REACH);
        Set<BlockPos> ice = new HashSet<>();
        int surface = Integer.MIN_VALUE;
        for (int x = centre.getX() - reach; x <= centre.getX() + reach; x++) {
            for (int z = centre.getZ() - reach; z <= centre.getZ() + reach; z++) {
                BlockPos column = top(lunar, new BlockPos(x, 0, z));
                if (lunar.getBlockState(column).is(GooBlocks.CRATER_ICE.get())) {
                    ice.add(column);
                    surface = Math.max(surface, column.getY());
                }
            }
        }
        if (ice.isEmpty()) {
            return Optional.empty();
        }
        int level = surface;
        Set<BlockPos> outline = new HashSet<>();
        for (BlockPos block : ice) {
            if (block.getY() == level && bordersOtherThanIce(lunar, block)) {
                outline.add(block.subtract(centre).atY(0));
            }
        }
        return Optional.of(new Pool(centre, level, outline));
    }

    private static boolean bordersOtherThanIce(ServerLevel lunar, BlockPos block) {
        for (Direction side : Direction.Plane.HORIZONTAL) {
            if (!lunar.getBlockState(block.relative(side)).is(GooBlocks.CRATER_ICE.get())) {
                return true;
            }
        }
        return false;
    }

    private static void assertEdgeMeetsGround(GameTestHelper helper, ServerLevel lunar, Pool pool) {
        for (BlockPos offset : pool.outline()) {
            BlockPos block = pool.centre().offset(offset.getX(), pool.surface(), offset.getZ());
            for (Direction side : Direction.Plane.HORIZONTAL) {
                BlockPos beside = block.relative(side);
                BlockState besideState = lunar.getBlockState(beside);
                helper.assertFalse(besideState.isAir(), String.format(EDGE_MEETS_AIR, pool.centre(), pool.surface(),
                        block, besideState, beside));
            }
        }
    }

    /**
     * The first boulder field spots standing in a biome, walking chunks out from a spot deep in it; read from the
     * seeded masks alone, so no chunk generates while the search runs.
     */
    private static List<BlockPos> boulderSpotsIn(GameTestHelper helper, ServerLevel lunar, ResourceKey<Biome> biome,
            int wanted) {
        BlockPos spot = LunarSurfaceBiomes.spotIn(lunar, biome);
        helper.assertTrue(spot != null, String.format(NO_SPOT, biome.identifier()));
        Noise noise = lunar.getChunkSource().randomState().getOrCreateNoise(LunarRolls.CRATER_NOISE);
        List<BlockPos> found = new ArrayList<>();
        int baseX = spot.getX() >> 4;
        int baseZ = spot.getZ() >> 4;
        for (int ring = 0; ring <= CHUNK_REACH && found.size() < wanted; ring++) {
            for (int dx = -ring; dx <= ring && found.size() < wanted; dx++) {
                for (int dz = -ring; dz <= ring && found.size() < wanted; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
                        continue;
                    }
                    FieldMasks.boulderFieldSpots(noise, baseX + dx, baseZ + dz).stream()
                            .filter(candidate -> LunarSurfaceBiomes.surfaceBiomeAt(lunar, candidate.getX(),
                                    candidate.getZ()).is(biome))
                            .limit(wanted - found.size()).forEach(found::add);
                }
            }
        }
        helper.assertTrue(found.size() >= wanted, String.format(NO_BOULDER_SPOTS, CHUNK_REACH, spot,
                biome.identifier()));
        return found;
    }

    private static boolean isBoulderStone(BlockState state) {
        return state.is(GooBlocks.ANORTHOSITE.get()) || state.is(GooBlocks.BRECCIA.get()) || state.is(Blocks.BASALT);
    }

    private static int generatorGround(ServerLevel lunar, BlockPos column) {
        return lunar.getChunkSource().getGenerator().getBaseHeight(column.getX(), column.getZ(),
                Heightmap.Types.WORLD_SURFACE_WG, lunar, lunar.getChunkSource().randomState()) - 1;
    }

    private static BlockPos top(ServerLevel lunar, BlockPos column) {
        lunar.getChunk(column.getX() >> 4, column.getZ() >> 4);
        return column.atY(lunar.getHeight(Heightmap.Types.WORLD_SURFACE, column.getX(), column.getZ()) - 1);
    }
}
