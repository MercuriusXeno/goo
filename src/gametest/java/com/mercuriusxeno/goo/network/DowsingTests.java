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
 * Gametests for Astral's Dowsing laying its goo on the volume cell through
 * the real landing a stream's drips take: a drip striking a block's top
 * stands a one-layer Dowsing cell there, and a second drip on that cell
 * makes it two layers.
 * astral-world-goo-is-dowsing-an-x-ray-of-ore
 * drip-creates-a-volume-cell-immediately
 * drip-on-a-standing-cell-increases-it
 */
@RegistersGameTests
public final class DowsingTests {

    private static final Identifier DOWSING = Identifier.parse("goo:astral_dowsing");
    private static final String ABILITY_REQUIRED = "Ability registry must hold goo:astral_dowsing";
    private static final String DRIPS_LAY_DOWSING = "dowsing_drips_lay_a_cell_and_thicken_it";
    /** The cell the landed drips strike, walled in stone on a stone floor so nothing pours. */
    private static final BlockPos DRIPPED_CELL = new BlockPos(4, 1, 4);
    private static final String LAYERS_WRONG = "The dripped cell should hold %d layers of Dowsing, holds %s";

    private DowsingTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, DRIPS_LAY_DOWSING, DowsingTests::dowsingDripsLayACellAndThickenIt);
    }

    /**
     * One Dowsing drip striking a bare stone top stands a Dowsing cell of
     * one layer over it, and a second drip on that cell makes it two layers.
     * astral-world-goo-is-dowsing-an-x-ray-of-ore
     *
     * @param helper the gametest helper
     */
    public static void dowsingDripsLayACellAndThickenIt(GameTestHelper helper) {
        wallInOnStone(helper, DRIPPED_CELL);
        AbilityDefinition dowsing = AbilityRegistry.of(helper.getLevel()).getAbility(DOWSING);
        helper.assertTrue(dowsing != null, ABILITY_REQUIRED);
        ServerPlayer caster = SelfDeliveryTests.invoker(helper, GooTypes.ASTRAL);
        KnownRecipes.teachRequires(caster, dowsing);
        LandedDrips.landOn(helper, caster, dowsing, DRIPPED_CELL, 1);
        assertLayers(helper, DRIPPED_CELL, 1);
        LandedDrips.landOn(helper, caster, dowsing, DRIPPED_CELL, 1);
        assertLayers(helper, DRIPPED_CELL, 2);
        helper.getLevel().getServer().getPlayerList().remove(caster);
        helper.succeed();
    }

    /**
     * Lays a stone floor under a cell and stone on its four sides, so goo in it cannot pour.
     *
     * @param helper the gametest helper
     * @param cell   the cell
     */
    private static void wallInOnStone(GameTestHelper helper, BlockPos cell) {
        helper.setBlock(cell.below(), Blocks.STONE);
        for (Direction side : Direction.Plane.HORIZONTAL) {
            helper.setBlock(cell.relative(side), Blocks.STONE);
        }
    }

    private static void assertLayers(GameTestHelper helper, BlockPos cell, int layers) {
        BlockState state = helper.getBlockState(cell);
        Object held = state.is(GooBlocks.ASTRAL_DOWSING.get()) ? state.getValue(VolumeCellBlock.LAYERS) : state;
        helper.assertTrue(Integer.valueOf(layers).equals(held), String.format(LAYERS_WRONG, layers, held));
    }
}
