package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.lunar.CraterField;
import com.mercuriusxeno.goo.lunar.Lunar;
import com.mercuriusxeno.goo.registry.GooBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Gametests for lunar's crater ice pools: a pool fills the low ground it sits in, so at its edge the ice meets ground
 * standing at or over its surface, never open air a circle cut through; and two pools' outlines differ.
 * decision no-two-lunar-features-look-alike
 */
@RegistersGameTests
public final class LunarPoolTests {
    private static final String LUNAR_POOLS_FOLLOW_THE_GROUND = "lunar_pools_follow_the_ground";

    /** How many crater cells around a spot deep in the shadowed basins a search for pools reaches each way. */
    private static final int CELL_REACH = 24;
    /** A pool's ice is read this far out from its crater's centre, in radii, past its rim's fall. */
    private static final double READ_REACH = 1.7;
    private static final int POOLS_WANTED = 2;

    private static final String LUNAR_REQUIRED = "The server should hold goo:lunar";
    private static final String NO_BASINS = "No spot within the search lies deep in the shadowed basins";
    private static final String TOO_FEW_POOLS = "Within %d crater cells of %s, %d deep craters in the shadowed basins "
            + "hold a pool, fewer than %d";
    private static final String EDGE_MEETS_AIR = "The pool in the crater at %s, its surface at y %d, should meet ground "
            + "at its edge; the ice at %s borders %s at %s";
    private static final String OUTLINES_MATCH = "The pools in the craters at %s and %s should differ in outline";

    private LunarPoolTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, LUNAR_POOLS_FOLLOW_THE_GROUND, LunarPoolTests::lunarPoolsFollowTheGround);
    }

    /** One pool as read from the generated world: its crater's centre, its surface and its edge offsets. */
    private record Pool(BlockPos centre, int surface, Set<BlockPos> outline) {
    }

    /**
     * Finds two deep craters in goo:lunar's shadowed basins, the pools' home, whose floors hold crater ice; at every
     * ice block on each pool's surface whose side meets no ice, that side's block is ground, not air; and the two
     * pools' outlines, read from their craters' centres, differ.
     *
     * @param helper the gametest helper
     */
    public static void lunarPoolsFollowTheGround(GameTestHelper helper) {
        ServerLevel lunar = helper.getLevel().getServer().getLevel(Lunar.LEVEL);
        helper.assertTrue(lunar != null, LUNAR_REQUIRED);
        BlockPos basins = LunarSurfaceBiomes.spotIn(lunar, LunarSurfaceBiomes.SHADOWED_BASINS);
        helper.assertTrue(basins != null, NO_BASINS);
        CraterField craters = CraterField.seeded(lunar.getChunkSource().randomState()
                .getOrCreateNoise(CraterField.NOISE), lunar.getChunkSource().randomState());
        List<Pool> pools = new ArrayList<>();
        int baseX = Math.floorDiv(basins.getX(), CraterField.CELL);
        int baseZ = Math.floorDiv(basins.getZ(), CraterField.CELL);
        for (int dx = -CELL_REACH; dx <= CELL_REACH && pools.size() < POOLS_WANTED; dx++) {
            for (int dz = -CELL_REACH; dz <= CELL_REACH && pools.size() < POOLS_WANTED; dz++) {
                Optional<CraterField.Crater> crater = craters.craterInCell(baseX + dx, baseZ + dz)
                        .filter(found -> found.radius() >= CraterField.DEEP_CRATER_RADIUS)
                        .filter(found -> inBasins(lunar, found));
                crater.flatMap(found -> readPool(lunar, found)).ifPresent(pools::add);
            }
        }
        helper.assertTrue(pools.size() >= POOLS_WANTED, String.format(TOO_FEW_POOLS, CELL_REACH, basins, pools.size(),
                POOLS_WANTED));
        for (Pool pool : pools) {
            assertEdgeMeetsGround(helper, lunar, pool);
        }
        helper.assertFalse(pools.get(0).outline().equals(pools.get(1).outline()),
                String.format(OUTLINES_MATCH, pools.get(0).centre(), pools.get(1).centre()));
        helper.succeed();
    }

    private static boolean inBasins(ServerLevel lunar, CraterField.Crater crater) {
        double reach = crater.radius() * READ_REACH;
        for (int i = 0; i < 8; i++) {
            double angle = Math.PI * i / 4.0;
            int x = (int) Math.round(crater.centerX() + Math.cos(angle) * reach);
            int z = (int) Math.round(crater.centerZ() + Math.sin(angle) * reach);
            if (!LunarSurfaceBiomes.surfaceBiomeAt(lunar, x, z).is(LunarSurfaceBiomes.SHADOWED_BASINS)) {
                return false;
            }
        }
        return true;
    }

    /** The pool a crater's floor holds, read from the generated world, or empty where its floor holds no ice. */
    private static Optional<Pool> readPool(ServerLevel lunar, CraterField.Crater crater) {
        BlockPos centre = BlockPos.containing(crater.centerX(), 0.0, crater.centerZ());
        int reach = (int) Math.ceil(crater.radius() * READ_REACH);
        Set<BlockPos> ice = new HashSet<>();
        int surface = Integer.MIN_VALUE;
        for (int x = centre.getX() - reach; x <= centre.getX() + reach; x++) {
            for (int z = centre.getZ() - reach; z <= centre.getZ() + reach; z++) {
                lunar.getChunk(x >> 4, z >> 4);
                BlockPos top = new BlockPos(x, lunar.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1, z);
                if (lunar.getBlockState(top).is(GooBlocks.CRATER_ICE.get())) {
                    ice.add(top);
                    surface = Math.max(surface, top.getY());
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
}
