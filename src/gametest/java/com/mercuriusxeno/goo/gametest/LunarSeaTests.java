package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.lunar.LavaTubes;
import com.mercuriusxeno.goo.lunar.Lunar;
import com.mercuriusxeno.goo.lunar.LunarDepths;
import com.mercuriusxeno.goo.lunar.SeaCavern;
import com.mercuriusxeno.goo.registry.GooBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.RandomState;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Gametests for the molten iron sea filling the vast cavern over lunar's core: across a stretch of the cavern, an
 * island of iron core stands over the sea, and a pillar of anorthosite reaches from the sea floor to the roof.
 * decision lunar-underground-changes-by-depth-down-to-a-molten-iron-sea
 */
@RegistersGameTests
public final class LunarSeaTests {
    private static final String LUNAR_SEA_HOLDS_ISLANDS_AND_PILLARS = "lunar_sea_holds_islands_and_pillars";

    /** How far from the origin the sampled stretch reaches each way, in blocks. */
    private static final int STRETCH_REACH = 512;
    private static final int SAMPLE_STEP = 4;

    private static final String LUNAR_REQUIRED = "The server should hold goo:lunar";
    private static final String NO_ISLAND = "No column within %d blocks of the origin stands an island over the sea";
    private static final String NO_PILLAR = "No column within %d blocks of the origin stands a pillar in the sea";
    private static final String ISLAND_OVER_THE_SEA = "The island column %s should read iron core at the sea's level y "
            + "%d, reads %s";
    private static final String PILLAR_FLOOR_TO_ROOF = "The pillar column %s should read anorthosite from the sea floor "
            + "to the roof, reads %s at y %d";

    private LunarSeaTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, LUNAR_SEA_HOLDS_ISLANDS_AND_PILLARS,
                LunarSeaTests::lunarSeaHoldsIslandsAndPillars);
    }

    /**
     * Samples columns on a grid across a stretch of goo:lunar's sea cavern through the seeded sea cavern; an island
     * column reads iron core at the sea's level, standing over the sea, and a pillar column no lava tube crosses
     * reads anorthosite from the sea floor to the roof.
     *
     * @param helper the gametest helper
     */
    public static void lunarSeaHoldsIslandsAndPillars(GameTestHelper helper) {
        ServerLevel lunar = helper.getLevel().getServer().getLevel(Lunar.LEVEL);
        helper.assertTrue(lunar != null, LUNAR_REQUIRED);
        RandomState randomState = lunar.getChunkSource().randomState();
        SeaCavern sea = SeaCavern.seeded(randomState.getOrCreateNoise(SeaCavern.FLOOR_NOISE),
                randomState.getOrCreateNoise(SeaCavern.ROOF_NOISE), randomState.getOrCreateNoise(SeaCavern.PILLAR_NOISE));
        BlockPos island = findColumn(column -> sea.isIslandAt(column.getX(), column.getZ()));
        helper.assertTrue(island != null, String.format(NO_ISLAND, STRETCH_REACH));
        LavaTubes tubes = LavaTubes.seeded(randomState.getOrCreateNoise(LavaTubes.NOISE));
        BlockPos pillar = findColumn(column -> sea.isPillarAt(column.getX(), column.getZ())
                && !tubes.crossesColumnAt(column.getX(), column.getZ()));
        helper.assertTrue(pillar != null, String.format(NO_PILLAR, STRETCH_REACH));
        lunar.getChunk(island.getX() >> 4, island.getZ() >> 4);
        BlockState atSeaLevel = lunar.getBlockState(island.atY(LunarDepths.SEA_LEVEL_Y));
        helper.assertTrue(atSeaLevel.is(GooBlocks.IRON_CORE.get()),
                String.format(ISLAND_OVER_THE_SEA, island, LunarDepths.SEA_LEVEL_Y, atSeaLevel));
        lunar.getChunk(pillar.getX() >> 4, pillar.getZ() >> 4);
        int roofY = sea.roofYAt(pillar.getX(), pillar.getZ());
        for (int y = sea.floorYAt(pillar.getX(), pillar.getZ()) + 1; y < roofY; y++) {
            BlockState rock = lunar.getBlockState(pillar.atY(y));
            helper.assertTrue(rock.is(GooBlocks.ANORTHOSITE.get()), String.format(PILLAR_FLOOR_TO_ROOF, pillar, rock, y));
        }
        helper.succeed();
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
