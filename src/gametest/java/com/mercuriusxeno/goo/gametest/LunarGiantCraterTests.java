package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.lunar.CraterField;
import com.mercuriusxeno.goo.lunar.GiantCraterField;
import com.mercuriusxeno.goo.lunar.Lunar;
import com.mercuriusxeno.goo.lunar.RilleField;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Gametests for lunar's giant craters: at a generated giant crater, found through the seeded giant crater field, the
 * surface on its rim stands above the surface on its floor, and its central peak stands above the floor too.
 * decision lunar-surface-gains-giant-craters-mountains-rilles-and-boulder-fields
 */
@RegistersGameTests
public final class LunarGiantCraterTests {
    private static final String LUNAR_GIANT_CRATERS_RAISE_RIMS_AND_PEAKS = "lunar_giant_craters_raise_rims_and_peaks";

    /** How many giant crater cells from the origin a search reaches each way. */
    private static final int CELL_REACH = 8;
    /** The floor column stands this many radii out from the centre, between the peak and the terraces. */
    private static final double FLOOR_REACH = 0.3;
    /**
     * A basin reading past which lunar's mountain ranges stand nowhere; a crater whose three columns read past it
     * shows its own shape alone.
     */
    private static final double DEEP_IN_A_BASIN = 0.25;

    private static final String LUNAR_REQUIRED = "The server should hold goo:lunar";
    private static final String NO_GIANT_CRATER = "No giant crater within %d cells of the origin lies wholly in a basin";
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
    }

    /**
     * Finds a giant crater in goo:lunar through the seeded giant crater field whose centre, floor and rim columns all
     * lie deep in a basin, where no mountain range stands, and reads the generated surface at each: the rim and the
     * centre both stand over the floor.
     *
     * @param helper the gametest helper
     */
    public static void lunarGiantCratersRaiseRimsAndPeaks(GameTestHelper helper) {
        ServerLevel lunar = helper.getLevel().getServer().getLevel(Lunar.LEVEL);
        helper.assertTrue(lunar != null, LUNAR_REQUIRED);
        RandomState randomState = lunar.getChunkSource().randomState();
        GiantCraterField giants = GiantCraterField.seeded(randomState.getOrCreateNoise(CraterField.NOISE));
        NormalNoise maria = randomState.getOrCreateNoise(RilleField.MARIA_NOISE);
        GiantCraterField.Crater crater = findCraterInABasin(giants, maria);
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

    private static GiantCraterField.Crater findCraterInABasin(GiantCraterField giants, NormalNoise maria) {
        for (int cellX = -CELL_REACH; cellX <= CELL_REACH; cellX++) {
            for (int cellZ = -CELL_REACH; cellZ <= CELL_REACH; cellZ++) {
                Optional<GiantCraterField.Crater> crater = giants.craterInCell(cellX, cellZ);
                if (crater.isPresent() && liesInABasin(crater.get(), maria)) {
                    return crater.get();
                }
            }
        }
        return null;
    }

    private static boolean liesInABasin(GiantCraterField.Crater crater, NormalNoise maria) {
        for (double reach : new double[] {0.0, FLOOR_REACH, 1.0}) {
            if (maria.getValue(crater.centerX() + crater.radius() * reach, 0.0, crater.centerZ()) <= DEEP_IN_A_BASIN) {
                return false;
            }
        }
        return true;
    }

    private static int surfaceY(ChunkGenerator generator, ServerLevel lunar, RandomState randomState, BlockPos column) {
        return generator.getBaseHeight(column.getX(), column.getZ(), Heightmap.Types.WORLD_SURFACE_WG, lunar, randomState);
    }
}
