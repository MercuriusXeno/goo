package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.block.ability.VolumeCellBlock;
import com.mercuriusxeno.goo.gametest.GooTestFunctions;
import com.mercuriusxeno.goo.gametest.KnownRecipes;
import com.mercuriusxeno.goo.gametest.RegistersGameTests;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;

/**
 * Frost's Rime through the real landing its stream's drips take: a drip
 * striking stone's top lays a one-layer Rime cell there, as slippery as ice.
 * iceborn-is-dropped-for-rime-a-frost-goo-that-keeps-what-is-around-it-frozen
 * rime-goo-is-slippery-to-walk-on
 */
@RegistersGameTests
public final class RimeTests {

    private static final String RIME_DRIP_LAYS_AN_ICY_CELL = "rime_drip_lays_an_icy_cell_on_stone";
    private static final Identifier RIME = Identifier.parse("goo:frost_rime");
    /** The floor block a drip strikes, in the middle of the bay. */
    private static final BlockPos FLOOR = new BlockPos(2, 1, 2);
    /** The cell over the floor block, where the drip's layer lands. */
    private static final BlockPos OVER_THE_FLOOR = FLOOR.above();
    private static final String ABILITY_REQUIRED = "The ability registry must hold frost_rime";
    private static final String LAYERS_WRONG = "The cell over the stone should hold one layer of Rime, stands %s";
    private static final String FRICTION_WRONG = "The Rime cell should stand at ice's friction %s, stands at %s";

    private RimeTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, RIME_DRIP_LAYS_AN_ICY_CELL, RimeTests::rimeDripLaysAnIcyCellOnStone);
    }

    /**
     * One Rime drip striking a stone block's top, from a frost-holding caster
     * who pays for its layer, lays a Rime cell of one layer over it, standing
     * at ice's friction.
     *
     * @param helper the gametest helper
     */
    public static void rimeDripLaysAnIcyCellOnStone(GameTestHelper helper) {
        helper.setBlock(FLOOR, Blocks.STONE);
        helper.setBlock(OVER_THE_FLOOR, Blocks.AIR);
        AbilityDefinition rime = AbilityRegistry.of(helper.getLevel()).getAbility(RIME);
        helper.assertTrue(rime != null, ABILITY_REQUIRED);
        ServerPlayer caster = SelfDeliveryTests.invoker(helper, GooTypes.FROST);
        KnownRecipes.teachRequires(caster, rime);
        LandedDrips.landOn(helper, caster, rime, FLOOR, 1);
        helper.getLevel().getServer().getPlayerList().remove(caster);
        BlockState state = helper.getBlockState(OVER_THE_FLOOR);
        boolean oneLayer = BuiltInRegistries.BLOCK.getKey(state.getBlock()).equals(RIME)
                && state.getValue(VolumeCellBlock.LAYERS) == 1;
        helper.assertTrue(oneLayer, String.format(LAYERS_WRONG, state));
        float ice = Blocks.ICE.getFriction();
        float friction = state.getBlock().getFriction();
        helper.assertTrue(friction == ice, String.format(FRICTION_WRONG, ice, friction));
        helper.succeed();
    }
}
