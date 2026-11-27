package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.lunar.GiantCraterField;
import com.mercuriusxeno.goo.lunar.Lunar;
import com.mercuriusxeno.goo.lunar.LunarRolls;
import com.mercuriusxeno.goo.registry.GooBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;

/**
 * Gametests for lunar's giant craters: at a generated giant crater, found through the seeded giant crater field, the
 * surface on its rim stands above the surface on its floor, and its central peak stands above the floor too.
 * decision lunar-surface-gains-giant-craters-mountains-rilles-and-boulder-fields
 */
@RegistersGameTests
public final class LunarGiantCraterTests {
    private static final String LUNAR_GIANT_CRATERS_RAISE_RIMS_AND_PEAKS = "lunar_giant_craters_raise_rims_and_peaks";
    private static final String GIANT_CRATER_POOLS_LIE_FLAT_IN_LOW_GROUND = "giant_crater_pools_lie_flat_in_low_ground";
    private static final String GIANT_CRATERS_THROW_BOULDERS = "giant_craters_throw_boulders";
    /** The floor ring the pool is read around, in radii, and how many columns around it. */
    private static final double POOL_RING_REACH = 0.3;
    private static final int POOL_RING_COLUMNS = 32;
    /** A thrown boulder stands at least this far over the ground the generator laid under it. */
    private static final int BOULDER_RISE = 3;
    private static final String PEAK_DRY = "The giant crater at %s should leave its central peak free of ice, reads %s at "
            + "its top y %d";
    private static final String POOL_FLAT = "The giant crater at %s should hold one flat frozen pool round its floor, "
            + "reads ice tops at %s";
    private static final String BOULDER_THROWN = "The giant crater at %s should throw a boulder of anorthosite, breccia "
            + "or basalt standing %d over the ground at %s, reads %s at y %d over ground at y %d";
    private static final String NO_BOULDERS = "The giant crater at %s should throw at least one boulder";

    /** How many giant crater cells from the origin a search reaches each way. */
    private static final int CELL_REACH = 16;
    /** The floor column stands this many radii out from the centre, between the peak and the terraces. */
    private static final double FLOOR_REACH = 0.3;
    /** A crater lies in the basins where the basins hold its centre, floor and rim at this many angles round it. */
    private static final int BASIN_CHECK_ANGLES = 12;

    private static final String LUNAR_REQUIRED = "The server should hold goo:lunar";
    private static final String NO_GIANT_CRATER = "No giant crater within %d cells of the origin lies wholly in the "
            + "shadowed basins";
    private static final String RIM_OVER_FLOOR = "The giant crater at %s should stand its rim %s at y %d over its floor "
            + "%s at y %d";
    private static final String PEAK_OVER_FLOOR = "The giant crater at %s should stand its centre at y %d over its "
            + "floor %s at y %d";

    private LunarGiantCraterTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, LUNAR_GIANT_CRATERS_RAISE_RIMS_AND_PEAKS,
                LunarGiantCraterTests::lunarGiantCratersRaiseRimsAndPeaks);
        GooTestFunctions.registerTest(registrar, GIANT_CRATER_POOLS_LIE_FLAT_IN_LOW_GROUND,
                LunarGiantCraterTests::giantCraterPoolsLieFlatInLowGround);
        GooTestFunctions.registerTest(registrar, GIANT_CRATERS_THROW_BOULDERS, LunarGiantCraterTests::giantCratersThrowBoulders);
    }

    /**
     * Finds a giant crater in goo:lunar through the seeded giant crater field whose centre, floor and rim columns all
     * lie in the shadowed basins, where no mountain range stands, and reads the generated surface at each: the rim and the
     * centre both stand over the floor.
     *
     * @param helper the gametest helper
     */
    public static void lunarGiantCratersRaiseRimsAndPeaks(GameTestHelper helper) {
        ServerLevel lunar = helper.getLevel().getServer().getLevel(Lunar.LEVEL);
        helper.assertTrue(lunar != null, LUNAR_REQUIRED);
        RandomState randomState = lunar.getChunkSource().randomState();
        GiantCraterField giants = GiantCraterField.seeded(randomState.getOrCreateNoise(LunarRolls.CRATER_NOISE), randomState);
        GiantCraterField.Crater crater = findCraterInABasin(giants, lunar);
        helper.assertTrue(crater != null, String.format(NO_GIANT_CRATER, CELL_REACH));
        BlockPos centre = BlockPos.containing(crater.centerX(), 0.0, crater.centerZ());
        BlockPos floor = centre.offset((int) Math.round(crater.radius() * FLOOR_REACH), 0, 0);
        BlockPos rim = centre.offset((int) Math.round(crater.radius()), 0, 0);
        ChunkGenerator generator = lunar.getChunkSource().getGenerator();
        int centreY = surfaceY(generator, lunar, randomState, centre);
        int floorY = surfaceY(generator, lunar, randomState, floor);
        int rimY = surfaceY(generator, lunar, randomState, rim);
        helper.assertTrue(rimY > floorY, String.format(RIM_OVER_FLOOR, centre, rim, rimY, floor, floorY));
        helper.assertTrue(centreY > floorY, String.format(PEAK_OVER_FLOOR, centre, centreY, floor, floorY));
        helper.succeed();
    }

    /**
     * A giant crater in goo:lunar, lying in the shadowed basins, freezes a pool in its floor's low ground: every frozen column
     * round its floor ring tops out at one level, at least one does, and its central peak stands dry over the pool.
     * decision lunar-ground-gains-meteoric-iron-impact-glass-breccia-and-crater-ice
     *
     * @param helper the gametest helper
     */
    public static void giantCraterPoolsLieFlatInLowGround(GameTestHelper helper) {
        ServerLevel lunar = helper.getLevel().getServer().getLevel(Lunar.LEVEL);
        helper.assertTrue(lunar != null, LUNAR_REQUIRED);
        GiantCraterField.Crater crater = craterInABasin(helper, lunar);
        BlockPos centre = BlockPos.containing(crater.centerX(), 0.0, crater.centerZ());
        BlockPos peak = top(lunar, centre);
        helper.assertFalse(lunar.getBlockState(peak).is(GooBlocks.CRATER_ICE.get()),
                String.format(PEAK_DRY, centre, lunar.getBlockState(peak), peak.getY()));
        Set<Integer> iceTops = new TreeSet<>();
        for (int i = 0; i < POOL_RING_COLUMNS; i++) {
            double angle = 2.0 * Math.PI * i / POOL_RING_COLUMNS;
            BlockPos column = BlockPos.containing(crater.centerX() + Math.cos(angle) * crater.radius() * POOL_RING_REACH,
                    0.0, crater.centerZ() + Math.sin(angle) * crater.radius() * POOL_RING_REACH);
            BlockPos surface = top(lunar, column);
            if (lunar.getBlockState(surface).is(GooBlocks.CRATER_ICE.get())) {
                iceTops.add(surface.getY());
            }
        }
        helper.assertTrue(iceTops.size() == 1, String.format(POOL_FLAT, centre, iceTops));
        helper.succeed();
    }

    /**
     * A giant crater in goo:lunar, lying in the shadowed basins, throws boulders: at the first spot the seeded giant crater
     * field throws one to inside the basins, the boulders' one home, a boulder of anorthosite, breccia or basalt stands over the ground the generator laid.
     * decision lunar-surface-gains-giant-craters-mountains-rilles-and-boulder-fields
     *
     * @param helper the gametest helper
     */
    public static void giantCratersThrowBoulders(GameTestHelper helper) {
        ServerLevel lunar = helper.getLevel().getServer().getLevel(Lunar.LEVEL);
        helper.assertTrue(lunar != null, LUNAR_REQUIRED);
        GiantCraterField.Crater crater = craterInABasin(helper, lunar);
        BlockPos centre = BlockPos.containing(crater.centerX(), 0.0, crater.centerZ());
        List<BlockPos> spots = crater.boulderSpots().stream().filter(spot -> LunarSurfaceBiomes.surfaceBiomeAt(lunar,
                spot.getX(), spot.getZ()).is(LunarSurfaceBiomes.SHADOWED_BASINS)).toList();
        helper.assertFalse(spots.isEmpty(), String.format(NO_BOULDERS, centre));
        BlockPos spot = spots.get(0);
        BlockPos surface = top(lunar, spot);
        BlockState stone = lunar.getBlockState(surface);
        RandomState randomState = lunar.getChunkSource().randomState();
        int ground = surfaceY(lunar.getChunkSource().getGenerator(), lunar, randomState, spot) - 1;
        boolean boulderStone = stone.is(GooBlocks.ANORTHOSITE.get()) || stone.is(GooBlocks.BRECCIA.get())
                || stone.is(Blocks.BASALT);
        helper.assertTrue(boulderStone && surface.getY() >= ground + BOULDER_RISE, String.format(BOULDER_THROWN,
                centre, BOULDER_RISE, spot, stone, surface.getY(), ground));
        helper.succeed();
    }

    private static GiantCraterField.Crater craterInABasin(GameTestHelper helper, ServerLevel lunar) {
        RandomState randomState = lunar.getChunkSource().randomState();
        GiantCraterField giants = GiantCraterField.seeded(randomState.getOrCreateNoise(LunarRolls.CRATER_NOISE), randomState);
        GiantCraterField.Crater crater = findCraterInABasin(giants, lunar);
        helper.assertTrue(crater != null, String.format(NO_GIANT_CRATER, CELL_REACH));
        return crater;
    }

    private static BlockPos top(ServerLevel lunar, BlockPos column) {
        lunar.getChunk(column.getX() >> 4, column.getZ() >> 4);
        return column.atY(lunar.getHeight(Heightmap.Types.WORLD_SURFACE, column.getX(), column.getZ()) - 1);
    }

    private static GiantCraterField.Crater findCraterInABasin(GiantCraterField giants, ServerLevel lunar) {
        for (int cellX = -CELL_REACH; cellX <= CELL_REACH; cellX++) {
            for (int cellZ = -CELL_REACH; cellZ <= CELL_REACH; cellZ++) {
                GiantCraterField.Crater crater = giants.craterInCell(cellX, cellZ);
                if (liesInABasin(crater, lunar)) {
                    return crater;
                }
            }
        }
        return null;
    }

    /**
     * Whether a crater's centre, floor and rim columns all stand in the shadowed basins, where its pools freeze, its
     * boulders fall and no mountain range stands.
     * decision lunar-surface-has-maria-highlands-shadowed-basins-and-eldritch-ground
     */
    private static boolean liesInABasin(GiantCraterField.Crater crater, ServerLevel lunar) {
        for (double reach : new double[] {0.0, FLOOR_REACH, 1.0}) {
            for (int i = 0; i < BASIN_CHECK_ANGLES; i++) {
                double angle = 2.0 * Math.PI * i / BASIN_CHECK_ANGLES;
                int x = (int) Math.round(crater.centerX() + Math.cos(angle) * crater.radius() * reach);
                int z = (int) Math.round(crater.centerZ() + Math.sin(angle) * crater.radius() * reach);
                if (!LunarSurfaceBiomes.surfaceBiomeAt(lunar, x, z).is(LunarSurfaceBiomes.SHADOWED_BASINS)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static int surfaceY(ChunkGenerator generator, ServerLevel lunar, RandomState randomState, BlockPos column) {
        return generator.getBaseHeight(column.getX(), column.getZ(), Heightmap.Types.WORLD_SURFACE_WG, lunar, randomState);
    }
}
