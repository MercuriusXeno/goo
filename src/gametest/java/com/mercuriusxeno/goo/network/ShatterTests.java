package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.block.ability.VolumeCellBlock;
import com.mercuriusxeno.goo.gametest.GooTestFunctions;
import com.mercuriusxeno.goo.gametest.KnownRecipes;
import com.mercuriusxeno.goo.gametest.RegistersGameTests;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;

/**
 * Crystal's Shatter through the real landing its stream's drips take: a
 * drip striking stone's top lays a one-layer crystal cell there.
 * shatter-is-splattered-goo-and-shards-form-over-the-splatters
 */
@RegistersGameTests
public final class ShatterTests {

    private static final String SHATTER_DRIP_LAYS_A_CELL = "shatter_drip_lays_a_cell_on_stone";
    private static final Identifier SHATTER = Identifier.parse("goo:crystal_shatter");
    /** The floor block a drip strikes, in the middle of the bay. */
    private static final BlockPos FLOOR = new BlockPos(2, 1, 2);
    /** The cell over the floor block, where the drip's layer lands. */
    private static final BlockPos OVER_THE_FLOOR = FLOOR.above();
    private static final String ABILITY_REQUIRED = "The ability registry must hold crystal_shatter";
    private static final String LAYERS_WRONG = "The cell over the stone should hold one layer of Shatter, stands %s";

    private ShatterTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, SHATTER_DRIP_LAYS_A_CELL, ShatterTests::shatterDripLaysACellOnStone);
    }

    /**
     * One Shatter drip striking a stone block's top lays a crystal cell of one layer over it.
     *
     * @param helper the gametest helper
     */
    public static void shatterDripLaysACellOnStone(GameTestHelper helper) {
        helper.setBlock(FLOOR, Blocks.STONE);
        helper.setBlock(OVER_THE_FLOOR, Blocks.AIR);
        AbilityDefinition shatter = AbilityRegistry.of(helper.getLevel()).getAbility(SHATTER);
        helper.assertTrue(shatter != null, ABILITY_REQUIRED);
        ServerPlayer caster = SelfDeliveryTests.invoker(helper, GooTypes.CRYSTAL);
        KnownRecipes.teachRequires(caster, shatter);
        LandedDrips.landOn(helper, caster, shatter, FLOOR, 1);
        helper.getLevel().getServer().getPlayerList().remove(caster);
        BlockState state = helper.getBlockState(OVER_THE_FLOOR);
        boolean oneLayer = state.is(GooBlocks.CRYSTAL_SHATTER.get()) && state.getValue(VolumeCellBlock.LAYERS) == 1;
        helper.assertTrue(oneLayer, String.format(LAYERS_WRONG, state));
        helper.succeed();
    }
}
