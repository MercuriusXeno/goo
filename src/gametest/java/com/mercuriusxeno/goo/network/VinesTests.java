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
 * Leaf's Vines through the real landing its stream's drips take: a drip
 * striking stone's top lays a one-layer Vines cell there.
 * vines-is-sprayed-slime-that-entangles-what-tries-to-cross
 */
@RegistersGameTests
public final class VinesTests {

    private static final String VINES_DRIP_LAYS_A_LEAF_CELL = "vines_drip_lays_a_leaf_cell_on_stone";
    private static final Identifier VINES = Identifier.parse("goo:leaf_vines");
    /** The floor block a drip strikes, in the middle of the bay. */
    private static final BlockPos FLOOR = new BlockPos(2, 1, 2);
    /** The cell over the floor block, where the drip's layer lands. */
    private static final BlockPos OVER_THE_FLOOR = FLOOR.above();
    private static final String ABILITY_REQUIRED = "The ability registry must hold leaf_vines";
    private static final String LAYERS_WRONG = "The cell over the stone should hold one layer of Vines, stands %s";

    private VinesTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, VINES_DRIP_LAYS_A_LEAF_CELL, VinesTests::vinesDripLaysALeafCellOnStone);
    }

    /**
     * One Vines drip landing on stone's top lays a Vines cell of one layer over it.
     *
     * @param helper the gametest helper
     */
    public static void vinesDripLaysALeafCellOnStone(GameTestHelper helper) {
        helper.setBlock(FLOOR, Blocks.STONE);
        helper.setBlock(OVER_THE_FLOOR, Blocks.AIR);
        AbilityDefinition vines = AbilityRegistry.of(helper.getLevel()).getAbility(VINES);
        helper.assertTrue(vines != null, ABILITY_REQUIRED);
        ServerPlayer caster = SelfDeliveryTests.invoker(helper, GooTypes.LEAF);
        KnownRecipes.teachRequires(caster, vines);
        LandedDrips.landOn(helper, caster, vines, FLOOR, 1);
        helper.getLevel().getServer().getPlayerList().remove(caster);
        BlockState state = helper.getBlockState(OVER_THE_FLOOR);
        boolean oneLayer = BuiltInRegistries.BLOCK.getKey(state.getBlock()).equals(VINES)
                && state.getValue(VolumeCellBlock.LAYERS) == 1;
        helper.assertTrue(oneLayer, String.format(LAYERS_WRONG, state));
        helper.succeed();
    }
}
