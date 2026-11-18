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
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;

/**
 * Unstable's Nitro as a world goo through the real paths: its stream's
 * drips lay a layer of a Nitro cell each. Each test walls its cells in stone
 * so no cell pours its layers away.
 * nitro-is-unstables-goo-that-explodes-in-a-chain-by-its-quantity
 */
@RegistersGameTests
public final class NitroTests {

    private static final String DRIP_LAYS_A_CELL = "nitro_drip_lays_an_unstable_cell_of_one_layer";
    private static final Identifier NITRO = Identifier.parse("goo:unstable_nitro");
    /** The floor block the drip strikes, walled on its four sides one block up. */
    private static final BlockPos DRIP_FLOOR = new BlockPos(2, 1, 2);
    private static final String ABILITY_REQUIRED = "The ability registry must hold unstable_nitro";
    private static final String DRIP_LAYERS_WRONG = "The cell over the floor should be Nitro of 1 layer, stands %s";

    private NitroTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, DRIP_LAYS_A_CELL, NitroTests::dripLaysAnUnstableCellOfOneLayer);
    }

    /**
     * One Nitro drip striking a walled stone top, from an unstable-holding
     * caster who pays for its layer, lays a Nitro cell of one layer.
     *
     * @param helper the gametest helper
     */
    public static void dripLaysAnUnstableCellOfOneLayer(GameTestHelper helper) {
        BlockPos cell = DRIP_FLOOR.above();
        helper.setBlock(DRIP_FLOOR, Blocks.STONE);
        helper.setBlock(cell, Blocks.AIR);
        for (Direction side : Direction.Plane.HORIZONTAL) {
            helper.setBlock(cell.relative(side), Blocks.STONE);
        }
        AbilityDefinition nitro = AbilityRegistry.of(helper.getLevel()).getAbility(NITRO);
        helper.assertTrue(nitro != null, ABILITY_REQUIRED);
        ServerPlayer caster = SelfDeliveryTests.invoker(helper, GooTypes.UNSTABLE);
        KnownRecipes.teachRequires(caster, nitro);
        LandedDrips.landOn(helper, caster, nitro, DRIP_FLOOR, 1);
        helper.getLevel().getServer().getPlayerList().remove(caster);
        BlockState state = helper.getBlockState(cell);
        helper.assertTrue(state.is(GooBlocks.UNSTABLE_NITRO.get()) && state.getValue(VolumeCellBlock.LAYERS) == 1,
                String.format(DRIP_LAYERS_WRONG, state));
        helper.succeed();
    }
}
