package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.block.ability.SideCoat;
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
 * makes it two layers; a drip striking a wall's side or a ceiling holds
 * there.
 * astral-world-goo-is-dowsing-an-x-ray-of-ore
 * astral-goo-hangs-and-is-loose
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
    private static final String DOWSING_CLINGS_TO_A_WALL = "dowsing_drip_on_a_wall_attaches_to_it";
    private static final String DOWSING_CLINGS_TO_A_CEILING = "dowsing_drip_under_a_ceiling_attaches_to_it";
    /** A stone block hung in open air, two blocks over the floor, whose west face and underside drips strike. */
    private static final BlockPos HUNG_BLOCK = new BlockPos(3, 4, 2);
    /** The open cell west of the hung block, beside its struck face. */
    private static final BlockPos BESIDE_THE_HUNG_BLOCK = HUNG_BLOCK.west();
    /** The open cell under the hung block, below its struck underside. */
    private static final BlockPos UNDER_THE_HUNG_BLOCK = HUNG_BLOCK.below();
    private static final String NOT_ON_THE_WALL = "A Dowsing drip striking a wall's side should leave a cell"
            + " attached to it, stands %s";
    private static final String NOT_ON_THE_CEILING = "A Dowsing drip striking a block's underside should leave a"
            + " cell attached to that ceiling, stands %s";

    private DowsingTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, DRIPS_LAY_DOWSING, DowsingTests::dowsingDripsLayACellAndThickenIt);
        GooTestFunctions.registerTest(registrar, DOWSING_CLINGS_TO_A_WALL, DowsingTests::dowsingDripOnAWallAttachesToIt);
        GooTestFunctions.registerTest(registrar, DOWSING_CLINGS_TO_A_CEILING,
                DowsingTests::dowsingDripUnderACeilingAttachesToIt);
    }

    /**
     * One Dowsing drip striking the west face of a stone block hung in open
     * air leaves an Astral cell beside that face, attached to it, since
     * Astral's adhesion hangs and so holds every rung below it.
     * astral-goo-hangs-and-is-loose
     * drip-striking-a-side-sticks-or-runs-by-adhesion
     *
     * @param helper the gametest helper
     */
    public static void dowsingDripOnAWallAttachesToIt(GameTestHelper helper) {
        hangABlock(helper);
        landOneDripOnSide(helper, Direction.WEST);
        BlockState cell = helper.getBlockState(BESIDE_THE_HUNG_BLOCK);
        boolean attached = cell.is(GooBlocks.ASTRAL_DOWSING.get())
                && VolumeCellBlock.coatOn(cell, Direction.EAST) == SideCoat.ATTACHED;
        helper.assertTrue(attached, String.format(NOT_ON_THE_WALL, cell));
        helper.succeed();
    }

    /**
     * One Dowsing drip striking the underside of a stone block hung in open
     * air leaves an Astral cell under it, attached to that ceiling.
     * astral-goo-hangs-and-is-loose
     * drip-striking-a-ceiling-runs-down-a-wall-or-drops
     *
     * @param helper the gametest helper
     */
    public static void dowsingDripUnderACeilingAttachesToIt(GameTestHelper helper) {
        hangABlock(helper);
        landOneDripOnSide(helper, Direction.DOWN);
        BlockState cell = helper.getBlockState(UNDER_THE_HUNG_BLOCK);
        boolean attached = cell.is(GooBlocks.ASTRAL_DOWSING.get()) && cell.getValue(VolumeCellBlock.CEILING);
        helper.assertTrue(attached, String.format(NOT_ON_THE_CEILING, cell));
        helper.succeed();
    }

    // Hangs a stone block in open air: the cells beside its west face and under it, and the column down to the
    // floor, stand open.
    private static void hangABlock(GameTestHelper helper) {
        helper.setBlock(HUNG_BLOCK, Blocks.STONE);
        for (int y = 1; y < HUNG_BLOCK.getY(); y++) {
            helper.setBlock(new BlockPos(HUNG_BLOCK.getX(), y, HUNG_BLOCK.getZ()), Blocks.AIR);
            helper.setBlock(new BlockPos(BESIDE_THE_HUNG_BLOCK.getX(), y, HUNG_BLOCK.getZ()), Blocks.AIR);
        }
        helper.setBlock(BESIDE_THE_HUNG_BLOCK, Blocks.AIR);
    }

    // Lands one Dowsing drip on a face of the hung block from an astral-holding caster, who pays for its layer.
    private static void landOneDripOnSide(GameTestHelper helper, Direction face) {
        AbilityDefinition dowsing = AbilityRegistry.of(helper.getLevel()).getAbility(DOWSING);
        helper.assertTrue(dowsing != null, ABILITY_REQUIRED);
        ServerPlayer caster = SelfDeliveryTests.invoker(helper, GooTypes.ASTRAL);
        KnownRecipes.teachRequires(caster, dowsing);
        LandedDrips.landOnSide(helper, caster, dowsing, HUNG_BLOCK, face, 1);
        helper.getLevel().getServer().getPlayerList().remove(caster);
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
