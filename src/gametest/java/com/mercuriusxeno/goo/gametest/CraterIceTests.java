package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.registry.GooBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;

/**
 * Gametests for crater ice, lunar's only water: a player breaking it over a solid block leaves water in its place, as
 * vanilla ice does, so the player can carry water to molten iron.
 * decision lunar-ground-gains-meteoric-iron-impact-glass-breccia-and-crater-ice
 */
@RegistersGameTests
public final class CraterIceTests {
    private static final String CRATER_ICE_BREAKS_TO_WATER = "crater_ice_breaks_to_water";

    private static final BlockPos STONE = new BlockPos(5, 1, 5);
    private static final BlockPos ICE = STONE.above();

    private static final String SHOULD_LEAVE_WATER = "Crater ice broken over stone should leave water at %s, reads %s";

    private CraterIceTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, CRATER_ICE_BREAKS_TO_WATER, CraterIceTests::craterIceBreaksToWater);
    }

    /**
     * A survival player breaks crater ice standing on stone, and water stands where the ice was.
     *
     * @param helper the gametest helper
     */
    public static void craterIceBreaksToWater(GameTestHelper helper) {
        helper.setBlock(STONE, Blocks.STONE);
        helper.setBlock(ICE, GooBlocks.CRATER_ICE.get());
        ServerPlayer player = SurvivalPlayers.placeIn(helper);
        player.gameMode.destroyBlock(helper.absolutePos(ICE));
        helper.getLevel().getServer().getPlayerList().remove(player);
        BlockState left = helper.getBlockState(ICE);
        helper.assertTrue(left.is(Blocks.WATER), String.format(SHOULD_LEAVE_WATER, ICE, left));
        helper.succeed();
    }
}
