package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.lunar.CraterField;
import com.mercuriusxeno.goo.lunar.Lunar;
import com.mercuriusxeno.goo.lunar.RilleField;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;

/**
 * Gametests for lunar's rilles: at a generated rille, found through the seeded rille field, the surface on the
 * rille's line stands lower than the surface on both its banks.
 * decision lunar-surface-gains-giant-craters-mountains-rilles-and-boulder-fields
 */
@RegistersGameTests
public final class LunarRilleTests {
    private static final String LUNAR_RILLES_SINK_BELOW_THEIR_BANKS = "lunar_rilles_sink_below_their_banks";

    /** How far from the origin a search for a rille reaches, in blocks. */
    private static final int SEARCH_REACH = 1024;
    private static final int SEARCH_STEP = 4;
    /** How far across from the line a bank is sought, in blocks. */
    private static final int BANK_REACH = 24;

    private static final String LUNAR_REQUIRED = "The server should hold goo:lunar";
    private static final String NO_RILLE = "No rille within %d blocks of the origin has a bank on each side clear of "
            + "every crater";
    private static final String SINKS_BELOW_BANKS = "The rille line %s at y %d should stand lower than its banks %s at "
            + "y %d and %s at y %d";

    private LunarRilleTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, LUNAR_RILLES_SINK_BELOW_THEIR_BANKS,
                LunarRilleTests::lunarRillesSinkBelowTheirBanks);
    }

    /**
     * Finds a column on a rille's line in goo:lunar through the seeded rille field, with the nearest uncut column on
     * each side of it along one axis as its banks, all three clear of every crater; the generated surface on the line
     * stands lower than on both banks.
     *
     * @param helper the gametest helper
     */
    public static void lunarRillesSinkBelowTheirBanks(GameTestHelper helper) {
        ServerLevel lunar = helper.getLevel().getServer().getLevel(Lunar.LEVEL);
        helper.assertTrue(lunar != null, LUNAR_REQUIRED);
        RandomState randomState = lunar.getChunkSource().randomState();
        RilleField rilles = RilleField.seeded(randomState.getOrCreateNoise(RilleField.LINE_NOISE),
                randomState.getOrCreateNoise(RilleField.MARIA_NOISE));
        CraterField craters = CraterField.seeded(randomState.getOrCreateNoise(CraterField.NOISE));
        BlockPos[] rille = findRille(rilles, craters);
        helper.assertTrue(rille != null, String.format(NO_RILLE, SEARCH_REACH));
        ChunkGenerator generator = lunar.getChunkSource().getGenerator();
        int lineY = surfaceY(generator, lunar, randomState, rille[0]);
        int firstBankY = surfaceY(generator, lunar, randomState, rille[1]);
        int secondBankY = surfaceY(generator, lunar, randomState, rille[2]);
        helper.assertTrue(lineY < firstBankY && lineY < secondBankY, String.format(SINKS_BELOW_BANKS, rille[0], lineY,
                rille[1], firstBankY, rille[2], secondBankY));
        helper.succeed();
    }

    private static int surfaceY(ChunkGenerator generator, ServerLevel lunar, RandomState randomState, BlockPos column) {
        return generator.getBaseHeight(column.getX(), column.getZ(), Heightmap.Types.WORLD_SURFACE_WG, lunar, randomState);
    }

    /** A line column and its two banks, or null where the search finds none. */
    private static BlockPos[] findRille(RilleField rilles, CraterField craters) {
        for (int x = -SEARCH_REACH; x <= SEARCH_REACH; x += SEARCH_STEP) {
            for (int z = -SEARCH_REACH; z <= SEARCH_REACH; z += SEARCH_STEP) {
                if (!rilles.isOnLineAt(x, z) || !craters.isOpenGroundAt(x, z)) {
                    continue;
                }
                BlockPos line = new BlockPos(x, 0, z);
                for (Direction across : new Direction[] {Direction.EAST, Direction.SOUTH}) {
                    BlockPos first = bank(rilles, craters, line, across);
                    BlockPos second = bank(rilles, craters, line, across.getOpposite());
                    if (first != null && second != null) {
                        return new BlockPos[] {line, first, second};
                    }
                }
            }
        }
        return null;
    }

    private static BlockPos bank(RilleField rilles, CraterField craters, BlockPos line, Direction across) {
        for (int step = 1; step <= BANK_REACH; step++) {
            BlockPos column = line.relative(across, step);
            if (rilles.cutAt(column.getX(), column.getZ()) == 0.0) {
                return craters.isOpenGroundAt(column.getX(), column.getZ()) ? column : null;
            }
        }
        return null;
    }
}
