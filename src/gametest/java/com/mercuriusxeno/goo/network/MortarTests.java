package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.blockmap.BlockMaps;
import com.mercuriusxeno.goo.ability.program.MortarStep;
import com.mercuriusxeno.goo.block.ability.SideCoat;
import com.mercuriusxeno.goo.block.ability.VolumeCellBlock;
import com.mercuriusxeno.goo.gametest.GooTestFunctions;
import com.mercuriusxeno.goo.gametest.KnownRecipes;
import com.mercuriusxeno.goo.gametest.RegistersGameTests;
import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;

/**
 * Rock's Mortar as a world goo through the real paths: a drip on a top lays
 * a Mortar cell of one layer, and a drip striking a wall's side holds there,
 * clinging where nothing is under it; a full cell petrifies the mob standing
 * on it and hardens the block it lies on a stage along the calcify map.
 * mortar-is-rocks-residual-a-petrification-and-hardening-goo
 * rock-goo-has-max-adhesion-and-just-below-max-cohesion
 * mortar-has-a-chance-to-deplete-as-it-petrifies-and-hardens
 */
@RegistersGameTests
public final class MortarTests {

    private static final String DRIP_LAYS_A_LAYER = "mortar_drip_lays_a_layer_on_a_top";
    private static final String DRIP_HOLDS_A_SIDE = "mortar_drip_holds_the_side_it_strikes";
    private static final String NO_HOLD_ON_A_STATUE = "mortar_drip_holds_no_statue_side";
    /** The statue the side drip strikes, two blocks over the bay floor, where the stone wall stands in its sibling. */
    private static final BlockPos STATUE = new BlockPos(3, 3, 4);
    private static final String PETRIFIES_AT_PETRIFYS_PACE = "mortar_petrifies_at_petrifys_pace";
    /** The full cell the pace cow stands on, laid on stone, which the calcify map hardens no further. */
    private static final BlockPos PACE_CELL = new BlockPos(5, 2, 5);
    /** Ticks the pace cow stands before its gauge is read. */
    private static final int PACE_TICKS = 20;
    /** The fills the gauge must hold by then: half the ticks, room for the cow to settle onto the cell. */
    private static final int PACE_FILLS = PACE_TICKS / 2;
    private static final String PETRIFIES_AND_HARDENS = "mortar_petrifies_its_mob_and_hardens_its_block";
    /** The full cell the cow stands on, laid on cobblestone, the calcify map's rung below stone that does not fall. */
    private static final BlockPos PETRIFY_CELL = new BlockPos(2, 2, 2);
    private static final Identifier MORTAR = Identifier.parse("goo:rock_mortar");
    /** The floor block the top drip strikes, walled on its four sides one block up. */
    private static final BlockPos DRIP_FLOOR = new BlockPos(2, 1, 2);
    /** The stone wall the side drip strikes, two blocks over the bay floor. */
    private static final BlockPos WALL = new BlockPos(3, 3, 2);
    /** The open cell beside the wall's west face, with nothing under it for two blocks. */
    private static final BlockPos BESIDE_THE_WALL = WALL.west();
    /** Ticks for a cell to pour whatever it will not hold, with room to spare. */
    private static final int POUR_TICKS = 20;
    private static final String ABILITY_REQUIRED = "The ability registry must hold rock_mortar";
    private static final String DRIP_LAYERS_WRONG = "The cell over the floor should be Mortar of 1 layer, stands %s";
    private static final String NOT_PETRIFIED = "The cow on the Mortar should petrify past %s, stands at %s";
    private static final String TOO_SLOW = "The cow should petrify %s or more in %d ticks, as Petrify's fog does, stands at %s";
    private static final String NOT_HARDENED = "The block under the Mortar should be %s, stands %s";
    private static final String HELD_BY_A_STATUE = "The cell beside the statue should hold no Mortar on it, stands %s";
    private static final String SIDE_NOT_HELD = "The cell beside the wall should be Mortar attached east, stands %s";

    private MortarTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, DRIP_LAYS_A_LAYER, MortarTests::dripLaysALayerOnATop);
        GooTestFunctions.registerTest(registrar, DRIP_HOLDS_A_SIDE, MortarTests::dripHoldsTheSideItStrikes);
        GooTestFunctions.registerTest(registrar, NO_HOLD_ON_A_STATUE, MortarTests::dripHoldsNoStatueSide);
        GooTestFunctions.registerTest(registrar, PETRIFIES_AT_PETRIFYS_PACE, MortarTests::petrifiesAtPetrifysPace);
        GooTestFunctions.registerTest(registrar, PETRIFIES_AND_HARDENS, MortarTests::petrifiesItsMobAndHardensItsBlock);
    }

    /**
     * One Mortar drip striking a walled stone top, from a rock-holding caster
     * who pays for its layer, lays a Mortar cell of one layer.
     *
     * @param helper the gametest helper
     */
    public static void dripLaysALayerOnATop(GameTestHelper helper) {
        BlockPos cell = DRIP_FLOOR.above();
        helper.setBlock(DRIP_FLOOR, Blocks.STONE);
        helper.setBlock(cell, Blocks.AIR);
        for (Direction side : Direction.Plane.HORIZONTAL) {
            helper.setBlock(cell.relative(side), Blocks.STONE);
        }
        AbilityDefinition mortar = mortar(helper);
        ServerPlayer caster = SelfDeliveryTests.invoker(helper, GooTypes.ROCK);
        KnownRecipes.teachRequires(caster, mortar);
        LandedDrips.landOn(helper, caster, mortar, DRIP_FLOOR, 1);
        helper.getLevel().getServer().getPlayerList().remove(caster);
        BlockState state = helper.getBlockState(cell);
        helper.assertTrue(state.is(GooBlocks.ROCK_MORTAR.get()) && state.getValue(VolumeCellBlock.LAYERS) == 1,
                String.format(DRIP_LAYERS_WRONG, state));
        helper.succeed();
    }

    /**
     * One Mortar drip striking a stone wall's west face beside an open cell
     * with nothing under it leaves Mortar in that cell attached to the wall
     * once the cell has had time to pour, where goo that does not cling would
     * run down the wall to the floor.
     *
     * @param helper the gametest helper
     */
    public static void dripHoldsTheSideItStrikes(GameTestHelper helper) {
        helper.setBlock(WALL, Blocks.STONE);
        helper.setBlock(BESIDE_THE_WALL, Blocks.AIR);
        helper.setBlock(BESIDE_THE_WALL.below(), Blocks.AIR);
        AbilityDefinition mortar = mortar(helper);
        ServerPlayer caster = SelfDeliveryTests.invoker(helper, GooTypes.ROCK);
        KnownRecipes.teachRequires(caster, mortar);
        LandedDrips.landOnSide(helper, caster, mortar, WALL, Direction.WEST, 1);
        helper.getLevel().getServer().getPlayerList().remove(caster);
        helper.runAfterDelay(POUR_TICKS, () -> {
            BlockState beside = helper.getBlockState(BESIDE_THE_WALL);
            boolean held = beside.is(GooBlocks.ROCK_MORTAR.get())
                    && VolumeCellBlock.coatOn(beside, Direction.EAST) == SideCoat.ATTACHED;
            helper.assertTrue(held, String.format(SIDE_NOT_HELD, beside));
            helper.succeed();
        });
    }

    /**
     * One Mortar drip striking a statue's west face beside an open cell with
     * nothing under it leaves no Mortar holding the statue once the cell has
     * had time to pour: a statue's sides are no solid wall, so the goo runs off
     * them as it runs off air.
     *
     * @param helper the gametest helper
     */
    public static void dripHoldsNoStatueSide(GameTestHelper helper) {
        BlockPos beside = STATUE.west();
        helper.setBlock(STATUE, GooBlocks.STATUE.get());
        helper.setBlock(beside, Blocks.AIR);
        helper.setBlock(beside.below(), Blocks.AIR);
        AbilityDefinition mortar = mortar(helper);
        ServerPlayer caster = SelfDeliveryTests.invoker(helper, GooTypes.ROCK);
        KnownRecipes.teachRequires(caster, mortar);
        LandedDrips.landOnSide(helper, caster, mortar, STATUE, Direction.WEST, 1);
        helper.getLevel().getServer().getPlayerList().remove(caster);
        helper.runAfterDelay(POUR_TICKS, () -> {
            BlockState cell = helper.getBlockState(beside);
            boolean held = cell.is(GooBlocks.ROCK_MORTAR.get())
                    && VolumeCellBlock.coatOn(cell, Direction.EAST) == SideCoat.ATTACHED;
            helper.assertFalse(held, String.format(HELD_BY_A_STATUE, cell));
            helper.succeed();
        });
    }

    /**
     * A cow standing on a full Mortar cell laid on cobblestone and walled in
     * stone gains petrification, and the cobblestone under the cell hardens to the
     * calcify map's next rung, once the cell's cadence has passed.
     *
     * @param helper the gametest helper
     */
    public static void petrifiesItsMobAndHardensItsBlock(GameTestHelper helper) {
        MortarStep step = MortarStep.in(mortar(helper).behaviors()).orElseThrow();
        Block hardened = BlockMaps.get(step.map()).flatMap(map -> map.next(Blocks.COBBLESTONE)).orElseThrow();
        helper.setBlock(PETRIFY_CELL.below(), Blocks.COBBLESTONE);
        for (Direction side : Direction.Plane.HORIZONTAL) {
            helper.setBlock(PETRIFY_CELL.relative(side), Blocks.STONE);
        }
        helper.setBlock(PETRIFY_CELL, GooBlocks.ROCK_MORTAR.get().defaultBlockState()
                .setValue(VolumeCellBlock.LAYERS, VolumeCellBlock.MAX_LAYERS));
        Mob cow = helper.spawnWithNoFreeWill(EntityTypes.COW, Vec3.atBottomCenterOf(PETRIFY_CELL.above()));
        float start = cow.getData(GooAttachments.PETRIFICATION).gauge();
        helper.succeedWhen(() -> {
            float gauge = cow.getData(GooAttachments.PETRIFICATION).gauge();
            helper.assertTrue(gauge > start, String.format(NOT_PETRIFIED, start, gauge));
            BlockState under = helper.getBlockState(PETRIFY_CELL.below());
            helper.assertTrue(under.is(hardened), String.format(NOT_HARDENED, hardened, under));
        });
    }

    /**
     * A cow standing on a full Mortar cell petrifies every tick by the step's
     * fill, as Petrify's fog fills it each held tick, so within a second its
     * gauge holds at least half a second's fills.
     *
     * @param helper the gametest helper
     */
    public static void petrifiesAtPetrifysPace(GameTestHelper helper) {
        MortarStep step = MortarStep.in(mortar(helper).behaviors()).orElseThrow();
        helper.setBlock(PACE_CELL.below(), Blocks.STONE);
        for (Direction side : Direction.Plane.HORIZONTAL) {
            helper.setBlock(PACE_CELL.relative(side), Blocks.STONE);
        }
        helper.setBlock(PACE_CELL, GooBlocks.ROCK_MORTAR.get().defaultBlockState()
                .setValue(VolumeCellBlock.LAYERS, VolumeCellBlock.MAX_LAYERS));
        Mob cow = helper.spawnWithNoFreeWill(EntityTypes.COW, Vec3.atBottomCenterOf(PACE_CELL.above()));
        float least = step.fillFor(cow) * PACE_FILLS;
        helper.runAfterDelay(PACE_TICKS, () -> {
            float gauge = cow.getData(GooAttachments.PETRIFICATION).gauge();
            helper.assertTrue(gauge >= least, String.format(TOO_SLOW, least, PACE_TICKS, gauge));
            helper.succeed();
        });
    }

    private static AbilityDefinition mortar(GameTestHelper helper) {
        AbilityDefinition mortar = AbilityRegistry.of(helper.getLevel()).getAbility(MORTAR);
        helper.assertTrue(mortar != null, ABILITY_REQUIRED);
        return mortar;
    }
}
