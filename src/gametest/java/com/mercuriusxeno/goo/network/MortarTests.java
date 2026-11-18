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
 * Rock's Mortar as a world goo through the real paths: a drip on a top lays
 * a Mortar cell of one layer, and a drip striking a wall's side holds there,
 * clinging where nothing is under it.
 * mortar-is-rocks-residual-a-petrification-and-hardening-goo
 * rock-goo-has-max-adhesion-and-just-below-max-cohesion
 */
@RegistersGameTests
public final class MortarTests {

    private static final String DRIP_LAYS_A_LAYER = "mortar_drip_lays_a_layer_on_a_top";
    private static final String DRIP_HOLDS_A_SIDE = "mortar_drip_holds_the_side_it_strikes";
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

    private static AbilityDefinition mortar(GameTestHelper helper) {
        AbilityDefinition mortar = AbilityRegistry.of(helper.getLevel()).getAbility(MORTAR);
        helper.assertTrue(mortar != null, ABILITY_REQUIRED);
        return mortar;
    }
}
