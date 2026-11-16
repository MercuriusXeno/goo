package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.program.MoltenStep;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;

/**
 * Blaze's Molten through the real landing its stream's drips take: a drip
 * striking stone's top lays a one-layer blaze cell there, and a drip
 * striking a lava source lays the cell on the lava's surface, where it stands.
 * molten-is-a-goopy-spray-that-turns-stone-below-it-to-lava
 */
@RegistersGameTests
public final class MoltenTests {

    private static final String MOLTEN_DRIP_LAYS_A_CELL = "molten_drip_lays_a_cell_on_stone";
    private static final String MOLTEN_POOLS_ON_LAVA = "molten_drip_pools_on_lava";
    private static final Identifier MOLTEN = Identifier.parse("goo:blaze_molten");
    /** The floor block a drip strikes, in the middle of the bay. */
    private static final BlockPos FLOOR = new BlockPos(2, 1, 2);
    /** The cell over the floor block, where the drip's layer lands. */
    private static final BlockPos OVER_THE_FLOOR = FLOOR.above();
    /** Ticks well past a blaze cell's wait between pours, so a cell that cannot stand there is gone. */
    private static final int PAST_THE_POUR = 20;
    private static final String ABILITY_REQUIRED = "The ability registry must hold blaze_molten";
    private static final String LAYERS_WRONG = "The cell over the %s should hold one layer of Molten, stands %s";
    private static final String LAVA_GONE = "The lava source should stand under the Molten cell, stands %s";
    private static final String MOLTEN_MELTS_STONE = "molten_over_stone_melts_it_for_three_layers";
    /** The layers the melt test lays over the stone. */
    private static final int FIVE_LAYERS = 5;
    /** The layers melting one block spends, stated here so the test checks the cell rather than echoing it. */
    private static final int MELT_LAYERS = 3;
    private static final String NOT_MELTED = "Five layers of Molten should melt the stone to a lava source, stands %s";
    private static final String MELTED_EARLY = "Half way through the heat the stone should stand under five layers,"
            + " stands %s under %s";
    private static final String LAYERS_LEFT_WRONG ="The Molten cell should rest on the lava at two layers, stands %s";

    private MoltenTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, MOLTEN_DRIP_LAYS_A_CELL, MoltenTests::moltenDripLaysACellOnStone);
        GooTestFunctions.registerTest(registrar, MOLTEN_POOLS_ON_LAVA, MoltenTests::moltenDripPoolsOnLava);
        GooTestFunctions.registerTest(registrar, MOLTEN_MELTS_STONE, MoltenTests::moltenOverStoneMeltsItForThreeLayers);
    }

    /**
     * One Molten drip striking a stone block's top lays a blaze cell of one layer over it.
     *
     * @param helper the gametest helper
     */
    public static void moltenDripLaysACellOnStone(GameTestHelper helper) {
        walledIn(helper, Blocks.STONE);
        landOneDrip(helper);
        assertOneLayer(helper, "stone");
        helper.succeed();
    }

    /**
     * One Molten drip striking a lava source lays the cell on the lava's
     * surface, and the cell still stands there, the lava source under it,
     * once its wait between pours has passed.
     *
     * @param helper the gametest helper
     */
    public static void moltenDripPoolsOnLava(GameTestHelper helper) {
        walledIn(helper, Blocks.LAVA);
        landOneDrip(helper);
        helper.runAfterDelay(PAST_THE_POUR, () -> {
            assertOneLayer(helper, "lava");
            BlockState under = helper.getBlockState(FLOOR);
            helper.assertTrue(under.is(Blocks.LAVA) && under.getFluidState().isSource(),
                    String.format(LAVA_GONE, under));
            helper.succeed();
        });
    }

    /**
     * Five layers of Molten over stone heat it red hot and then melt it: half
     * way through the heat the stone still stands under all five layers, and
     * after it a lava source stands where the stone stood, the cell resting on
     * it three layers lower, at two.
     * molten-spends-three-layers-per-stone-block-priced-at-lava-minus-mundane-block
     * stone-goes-red-hot-before-molten-turns-it-to-lava
     *
     * @param helper the gametest helper
     */
    public static void moltenOverStoneMeltsItForThreeLayers(GameTestHelper helper) {
        walledIn(helper, Blocks.STONE);
        for (Direction side : Direction.Plane.HORIZONTAL) {
            helper.setBlock(OVER_THE_FLOOR.relative(side), Blocks.STONE);
        }
        AbilityDefinition molten = AbilityRegistry.of(helper.getLevel()).getAbility(MOLTEN);
        helper.assertTrue(molten != null, ABILITY_REQUIRED);
        int heatTicks = MoltenStep.in(molten.behaviors()).orElseThrow().heatTicks();
        helper.setBlock(OVER_THE_FLOOR, GooBlocks.BLAZE_MOLTEN.get().defaultBlockState()
                .setValue(VolumeCellBlock.LAYERS, FIVE_LAYERS));
        helper.runAfterDelay(heatTicks / 2, () -> {
            BlockState heating = helper.getBlockState(FLOOR);
            BlockState cell = helper.getBlockState(OVER_THE_FLOOR);
            boolean fiveLayers = cell.is(GooBlocks.BLAZE_MOLTEN.get())
                    && cell.getValue(VolumeCellBlock.LAYERS) == FIVE_LAYERS;
            helper.assertTrue(heating.is(Blocks.STONE) && fiveLayers, String.format(MELTED_EARLY, heating, cell));
        });
        helper.runAfterDelay(heatTicks + PAST_THE_POUR, () -> {
            BlockState melted = helper.getBlockState(FLOOR);
            helper.assertTrue(melted.is(Blocks.LAVA) && melted.getFluidState().isSource(),
                    String.format(NOT_MELTED, melted));
            BlockState cell = helper.getBlockState(OVER_THE_FLOOR);
            boolean twoLayers = cell.is(GooBlocks.BLAZE_MOLTEN.get())
                    && cell.getValue(VolumeCellBlock.LAYERS) == FIVE_LAYERS - MELT_LAYERS;
            helper.assertTrue(twoLayers, String.format(LAYERS_LEFT_WRONG, cell));
            helper.succeed();
        });
    }

    /**
     * Stands a block on the floor in stone walls, so lava there cannot run off, with open air over it.
     */
    private static void walledIn(GameTestHelper helper, Block floor) {
        helper.setBlock(FLOOR.below(), Blocks.STONE);
        for (Direction side : Direction.Plane.HORIZONTAL) {
            helper.setBlock(FLOOR.relative(side), Blocks.STONE);
        }
        helper.setBlock(FLOOR, floor);
        helper.setBlock(OVER_THE_FLOOR, Blocks.AIR);
    }

    /**
     * Lands one Molten drip on the floor block's top from a blaze-holding caster, who pays for its layer.
     */
    private static void landOneDrip(GameTestHelper helper) {
        AbilityDefinition molten = AbilityRegistry.of(helper.getLevel()).getAbility(MOLTEN);
        helper.assertTrue(molten != null, ABILITY_REQUIRED);
        ServerPlayer caster = SelfDeliveryTests.invoker(helper, GooTypes.BLAZE);
        KnownRecipes.teachRequires(caster, molten);
        LandedDrips.landOn(helper, caster, molten, FLOOR, 1);
        helper.getLevel().getServer().getPlayerList().remove(caster);
    }

    private static void assertOneLayer(GameTestHelper helper, String floor) {
        BlockState state = helper.getBlockState(OVER_THE_FLOOR);
        boolean oneLayer = state.is(GooBlocks.BLAZE_MOLTEN.get()) && state.getValue(VolumeCellBlock.LAYERS) == 1;
        helper.assertTrue(oneLayer, String.format(LAYERS_WRONG, floor, state));
    }
}
