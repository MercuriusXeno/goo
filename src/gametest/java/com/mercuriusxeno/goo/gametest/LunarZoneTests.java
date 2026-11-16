package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.lunar.CrystalCaverns;
import com.mercuriusxeno.goo.lunar.LavaTubes;
import com.mercuriusxeno.goo.lunar.Lunar;
import com.mercuriusxeno.goo.lunar.LunarDepths;
import com.mercuriusxeno.goo.lunar.RubbleCaves;
import com.mercuriusxeno.goo.registry.GooBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Gametests for lunar's depth zones, each reading as its own place: the shallow zone's breccia and its rubble caves,
 * and the middle zone's caverns floored in crater ice and grown with impact glass.
 * decision lunar-underground-changes-by-depth-down-to-a-molten-iron-sea
 */
@RegistersGameTests
public final class LunarZoneTests {
    private static final String LUNAR_SHALLOW_ZONE_HOLDS_RUBBLE_CAVES = "lunar_shallow_zone_holds_rubble_caves";
    private static final String LUNAR_MIDDLE_ZONE_HOLDS_ICE_AND_CRYSTAL_CAVERNS =
            "lunar_middle_zone_holds_ice_and_crystal_caverns";
    /** How far from the origin a scan of the middle zone reaches each way, in blocks. */
    private static final int CAVERN_SCAN_REACH = 64;

    /** How far from the origin a search for a column reaches each way, in blocks. */
    private static final int STRETCH_REACH = 256;
    private static final int SAMPLE_STEP = 4;
    /** A solid column's surface stands this far over the shallow zone, so no crater or rille reaches the zone. */
    private static final int SOLID_SURFACE_CLEARANCE = 24;

    private static final String LUNAR_REQUIRED = "The server should hold goo:lunar";
    private static final String NO_SOLID_COLUMN = "No column within %d blocks of the origin runs solid through the "
            + "shallow zone";
    private static final String NO_CAVE = "No column within %d blocks of the origin holds a rubble cave";
    private static final String ZONE_READS_BRECCIA = "The solid column %s should read breccia through the shallow zone, "
            + "reads %s at y %d";
    private static final String NO_ICE_AND_CRYSTAL = "No air within %d blocks of the origin in the middle zone stands "
            + "on crater ice beside or under impact glass";
    private static final String CAVE_IS_OPEN = "The rubble cave column %s should read air at y %d, reads %s";
    private static final String CAVE_IN_BRECCIA = "The rubble cave column %s should read breccia %s the cave at y %d, "
            + "reads %s";

    private LunarZoneTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, LUNAR_SHALLOW_ZONE_HOLDS_RUBBLE_CAVES,
                LunarZoneTests::lunarShallowZoneHoldsRubbleCaves);
        GooTestFunctions.registerTest(registrar, LUNAR_MIDDLE_ZONE_HOLDS_ICE_AND_CRYSTAL_CAVERNS,
                LunarZoneTests::lunarMiddleZoneHoldsIceAndCrystalCaverns);
    }

    /**
     * In goo:lunar's shallow zone, a column clear of every cave and tube, its surface well over the zone, reads
     * breccia through the zone's band; and a cave column, found through the seeded rubble caves, reads air in the
     * cave with breccia under its floor and over its roof.
     *
     * @param helper the gametest helper
     */
    public static void lunarShallowZoneHoldsRubbleCaves(GameTestHelper helper) {
        ServerLevel lunar = helper.getLevel().getServer().getLevel(Lunar.LEVEL);
        helper.assertTrue(lunar != null, LUNAR_REQUIRED);
        RandomState randomState = lunar.getChunkSource().randomState();
        RubbleCaves caves = RubbleCaves.seeded(randomState.getOrCreateNoise(RubbleCaves.FOOTPRINT_NOISE),
                randomState.getOrCreateNoise(RubbleCaves.RUBBLE_NOISE));
        LavaTubes tubes = LavaTubes.seeded(randomState.getOrCreateNoise(LavaTubes.NOISE));
        Predicate<BlockPos> clearOfTubes = column -> !tubes.crossesColumnAt(column.getX(), column.getZ());
        BlockPos solid = findColumn(column -> !caves.holdsCaveAt(column.getX(), column.getZ())
                && clearOfTubes.test(column) && surfaceY(lunar, randomState, column)
                > LunarDepths.SHALLOW_ZONE_TOP_Y + SOLID_SURFACE_CLEARANCE);
        helper.assertTrue(solid != null, String.format(NO_SOLID_COLUMN, STRETCH_REACH));
        lunar.getChunk(solid.getX() >> 4, solid.getZ() >> 4);
        for (int y = LunarDepths.CAVERN_ZONE_TOP_Y; y < LunarDepths.SHALLOW_ZONE_TOP_Y; y++) {
            BlockState rock = lunar.getBlockState(solid.atY(y));
            helper.assertTrue(rock.is(GooBlocks.BRECCIA.get()), String.format(ZONE_READS_BRECCIA, solid, rock, y));
        }
        BlockPos cave = findColumn(column -> caves.holdsCaveAt(column.getX(), column.getZ()) && clearOfTubes.test(column)
                && surfaceY(lunar, randomState, column) > LunarDepths.SHALLOW_ZONE_TOP_Y + SOLID_SURFACE_CLEARANCE);
        helper.assertTrue(cave != null, String.format(NO_CAVE, STRETCH_REACH));
        assertCave(helper, lunar, caves, cave);
        helper.succeed();
    }

    /**
     * Scans the middle zone's band in goo:lunar across a stretch, at the columns the seeded crystal caverns open, for
     * a cavern's air standing on crater ice with impact glass beside it or over it.
     *
     * @param helper the gametest helper
     */
    public static void lunarMiddleZoneHoldsIceAndCrystalCaverns(GameTestHelper helper) {
        ServerLevel lunar = helper.getLevel().getServer().getLevel(Lunar.LEVEL);
        helper.assertTrue(lunar != null, LUNAR_REQUIRED);
        CrystalCaverns caverns = CrystalCaverns.seeded(lunar.getChunkSource().randomState()
                .getOrCreateNoise(CrystalCaverns.NOISE));
        for (int x = -CAVERN_SCAN_REACH; x <= CAVERN_SCAN_REACH; x++) {
            for (int z = -CAVERN_SCAN_REACH; z <= CAVERN_SCAN_REACH; z++) {
                for (int y = LunarDepths.WARREN_ZONE_TOP_Y; y < LunarDepths.CAVERN_ZONE_TOP_Y; y++) {
                    if (caverns.isOpenAt(x, y, z) && standsOnIceByGlass(lunar, new BlockPos(x, y, z))) {
                        helper.succeed();
                        return;
                    }
                }
            }
        }
        helper.fail(String.format(NO_ICE_AND_CRYSTAL, CAVERN_SCAN_REACH));
    }

    private static boolean standsOnIceByGlass(ServerLevel lunar, BlockPos air) {
        lunar.getChunk(air.getX() >> 4, air.getZ() >> 4);
        if (!lunar.getBlockState(air).isAir() || !lunar.getBlockState(air.below()).is(GooBlocks.CRATER_ICE.get())) {
            return false;
        }
        for (Direction side : Direction.values()) {
            if (side != Direction.DOWN && lunar.getBlockState(air.relative(side)).is(GooBlocks.IMPACT_GLASS.get())) {
                return true;
            }
        }
        return false;
    }

    private static void assertCave(GameTestHelper helper, ServerLevel lunar, RubbleCaves caves, BlockPos column) {
        lunar.getChunk(column.getX() >> 4, column.getZ() >> 4);
        int floorY = caves.floorYAt(column.getX(), column.getZ());
        int roofY = caves.roofYAt(column.getX(), column.getZ());
        BlockState inside = lunar.getBlockState(column.atY(floorY + 1));
        helper.assertTrue(inside.isAir(), String.format(CAVE_IS_OPEN, column, floorY + 1, inside));
        BlockState under = lunar.getBlockState(column.atY(floorY));
        helper.assertTrue(under.is(GooBlocks.BRECCIA.get()), String.format(CAVE_IN_BRECCIA, column, "under", floorY, under));
        BlockState over = lunar.getBlockState(column.atY(roofY));
        helper.assertTrue(over.is(GooBlocks.BRECCIA.get()), String.format(CAVE_IN_BRECCIA, column, "over", roofY, over));
    }

    private static int surfaceY(ServerLevel lunar, RandomState randomState, BlockPos column) {
        return lunar.getChunkSource().getGenerator().getBaseHeight(column.getX(), column.getZ(),
                Heightmap.Types.WORLD_SURFACE_WG, lunar, randomState);
    }

    private static BlockPos findColumn(Predicate<BlockPos> wanted) {
        for (int x = -STRETCH_REACH; x <= STRETCH_REACH; x += SAMPLE_STEP) {
            for (int z = -STRETCH_REACH; z <= STRETCH_REACH; z += SAMPLE_STEP) {
                BlockPos column = new BlockPos(x, 0, z);
                if (wanted.test(column)) {
                    return column;
                }
            }
        }
        return null;
    }
}
