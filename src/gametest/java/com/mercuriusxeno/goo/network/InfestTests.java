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
 * Nether's Infest as a world goo through the real paths: its stream's drips
 * lay a layer of an Infest cell each. Each test walls its cells in stone so
 * no cell pours its layers away.
 * infest-is-nethers-goo-the-decay-effect-localized-around-the-goo
 */
@RegistersGameTests
public final class InfestTests {

    private static final String DRIP_LAYS_A_LAYER = "infest_drip_lays_a_layer";
    private static final Identifier INFEST = Identifier.parse("goo:nether_infest");
    /** The floor block the drip strikes, walled on its four sides one block up. */
    private static final BlockPos DRIP_FLOOR = new BlockPos(2, 1, 2);
    private static final String ABILITY_REQUIRED = "The ability registry must hold nether_infest";
    private static final String DRIP_LAYERS_WRONG = "The cell over the floor should be Infest of 1 layer, stands %s";

    private InfestTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, DRIP_LAYS_A_LAYER, InfestTests::dripLaysALayer);
    }

    /**
     * One Infest drip striking a walled stone top, from a nether-holding
     * caster who pays for its layer, lays an Infest cell of one layer.
     *
     * @param helper the gametest helper
     */
    public static void dripLaysALayer(GameTestHelper helper) {
        BlockPos cell = DRIP_FLOOR.above();
        helper.setBlock(DRIP_FLOOR, Blocks.STONE);
        helper.setBlock(cell, Blocks.AIR);
        for (Direction side : Direction.Plane.HORIZONTAL) {
            helper.setBlock(cell.relative(side), Blocks.STONE);
        }
        AbilityDefinition infest = AbilityRegistry.of(helper.getLevel()).getAbility(INFEST);
        helper.assertTrue(infest != null, ABILITY_REQUIRED);
        ServerPlayer caster = SelfDeliveryTests.invoker(helper, GooTypes.NETHER);
        KnownRecipes.teachRequires(caster, infest);
        LandedDrips.landOn(helper, caster, infest, DRIP_FLOOR, 1);
        helper.getLevel().getServer().getPlayerList().remove(caster);
        BlockState state = helper.getBlockState(cell);
        helper.assertTrue(state.is(GooBlocks.NETHER_INFEST.get()) && state.getValue(VolumeCellBlock.LAYERS) == 1,
                String.format(DRIP_LAYERS_WRONG, state));
        helper.succeed();
    }
}
