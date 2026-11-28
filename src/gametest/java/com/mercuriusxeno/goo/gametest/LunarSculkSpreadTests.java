package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.registry.GooBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;

/**
 * Gametests for sculk growing in lunar rock: a sculk catalyst standing on anorthosite, and one on regolith, spreads
 * sculk into the rock under it when a mob a player kills dies beside it, as a catalyst spreads into deepslate in the
 * deep.
 * decision warren-sculk-homes-sensors-and-catalysts-and-grows-in-lunar-rock
 */
@RegistersGameTests
public final class LunarSculkSpreadTests {
    private static final String CATALYSTS_SPREAD_SCULK_INTO_ANORTHOSITE_AND_REGOLITH =
            "catalysts_spread_sculk_into_anorthosite_and_regolith";

    /** The anorthosite floor runs across the bay's west half and the regolith floor across its east half. */
    private static final int ANORTHOSITE_WEST = 0;
    private static final int REGOLITH_WEST = 3;
    private static final int HALF_WIDTH = 3;
    private static final int DEPTH = 6;
    private static final int FLOOR_Y = 1;
    private static final BlockPos ANORTHOSITE_CATALYST = new BlockPos(1, 2, 1);
    private static final BlockPos REGOLITH_CATALYST = new BlockPos(4, 2, 1);
    private static final BlockPos ANORTHOSITE_DEATH = new BlockPos(1, 2, 3);
    private static final BlockPos REGOLITH_DEATH = new BlockPos(4, 2, 3);

    private static final String NO_SCULK_IN = "No %s floor block beside its catalyst became sculk";

    private LunarSculkSpreadTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, CATALYSTS_SPREAD_SCULK_INTO_ANORTHOSITE_AND_REGOLITH,
                LunarSculkSpreadTests::catalystsSpreadSculkIntoAnorthositeAndRegolith);
    }

    /**
     * Lays a floor of anorthosite and one of regolith, a catalyst on each, and a player kills a zombie beside each;
     * at least one block of each floor becomes sculk.
     *
     * @param helper the gametest helper
     */
    public static void catalystsSpreadSculkIntoAnorthositeAndRegolith(GameTestHelper helper) {
        layFloor(helper, ANORTHOSITE_WEST, GooBlocks.ANORTHOSITE.get());
        layFloor(helper, REGOLITH_WEST, GooBlocks.REGOLITH.get());
        helper.setBlock(ANORTHOSITE_CATALYST, Blocks.SCULK_CATALYST);
        helper.setBlock(REGOLITH_CATALYST, Blocks.SCULK_CATALYST);
        ServerPlayer killer = SurvivalPlayers.placeIn(helper);
        killBeside(helper, killer, ANORTHOSITE_DEATH);
        killBeside(helper, killer, REGOLITH_DEATH);
        helper.getLevel().getServer().getPlayerList().remove(killer);
        helper.succeedWhen(() -> {
            helper.assertTrue(floorHoldsSculk(helper, ANORTHOSITE_WEST), String.format(NO_SCULK_IN, "anorthosite"));
            helper.assertTrue(floorHoldsSculk(helper, REGOLITH_WEST), String.format(NO_SCULK_IN, "regolith"));
        });
    }

    private static void layFloor(GameTestHelper helper, int west, Block rock) {
        for (int x = west; x < west + HALF_WIDTH; x++) {
            for (int z = 0; z < DEPTH; z++) {
                helper.setBlock(new BlockPos(x, FLOOR_Y, z), rock);
            }
        }
    }

    private static void killBeside(GameTestHelper helper, ServerPlayer killer, BlockPos at) {
        Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, at);
        zombie.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(killer), Float.MAX_VALUE);
    }

    private static boolean floorHoldsSculk(GameTestHelper helper, int west) {
        for (int x = west; x < west + HALF_WIDTH; x++) {
            for (int z = 0; z < DEPTH; z++) {
                if (helper.getBlockState(new BlockPos(x, FLOOR_Y, z)).is(Blocks.SCULK)) {
                    return true;
                }
            }
        }
        return false;
    }
}
