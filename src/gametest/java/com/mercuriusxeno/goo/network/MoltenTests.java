package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.program.MoltenStep;
import com.mercuriusxeno.goo.block.ability.MoltenHeats;
import com.mercuriusxeno.goo.block.ability.VolumeCellBlock;
import com.mercuriusxeno.goo.gametest.GooTestFunctions;
import com.mercuriusxeno.goo.gametest.KnownRecipes;
import com.mercuriusxeno.goo.gametest.RegistersGameTests;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.registry.GooServerState;
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
 * A cell melts obsidian and magma into lava and iron core into molten iron
 * for one layer each, and melts no other stone.
 * molten-is-a-goopy-spray-that-turns-stone-below-it-to-lava
 * molten-melts-only-obsidian-magma-and-iron-core-one-layer-each
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
    private static final String MOLTEN_MELTS_OBSIDIAN = "molten_over_obsidian_melts_it_for_one_layer";
    private static final String MOLTEN_MELTS_MAGMA = "molten_over_magma_melts_it_for_one_layer";
    private static final String MOLTEN_MELTS_IRON_CORE = "molten_over_iron_core_melts_it_to_molten_iron";
    private static final String MOLTEN_LEAVES_STONE = "molten_leaves_stone_unmelted";
    private static final String MOLTEN_LEAVES_COBBLESTONE = "molten_leaves_cobblestone_unmelted";
    private static final String MOLTEN_LEAVES_DEEPSLATE = "molten_leaves_deepslate_unmelted";
    private static final String MOLTEN_LEAVES_NETHERRACK = "molten_leaves_netherrack_unmelted";
    /** The layers the melt tests lay over the floor block. */
    private static final int TWO_LAYERS = 2;
    /** How many heats the unmelted tests wait out, so a melt that would run has run. */
    private static final int SEVERAL_HEATS = 4;
    private static final String NOT_MELTED = "Two layers of Molten should melt %s to a %s source, stands %s";
    private static final String MELTED_EARLY = "Half way through the heat %s should stand red hot under two layers,"
            + " stands %s under %s, heat started %s";
    private static final String LAYERS_LEFT_WRONG = "The Molten cell should rest on the melt at one layer, stands %s";
    private static final String HEAT_LEFT_STANDING = "The melt should end the cell's heat";
    private static final String MELTED_INTO_LAVA = "Fifteen layers of Molten should leave %s unmelted, stands %s";

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
        GooTestFunctions.registerTest(registrar, MOLTEN_MELTS_OBSIDIAN, MoltenTests::moltenOverObsidianMeltsItForOneLayer);
        GooTestFunctions.registerTest(registrar, MOLTEN_MELTS_MAGMA, MoltenTests::moltenOverMagmaMeltsItForOneLayer);
        GooTestFunctions.registerTest(registrar, MOLTEN_MELTS_IRON_CORE, MoltenTests::moltenOverIronCoreMeltsItToMoltenIron);
        GooTestFunctions.registerTest(registrar, MOLTEN_LEAVES_STONE, MoltenTests::moltenLeavesStoneUnmelted);
        GooTestFunctions.registerTest(registrar, MOLTEN_LEAVES_COBBLESTONE, MoltenTests::moltenLeavesCobblestoneUnmelted);
        GooTestFunctions.registerTest(registrar, MOLTEN_LEAVES_DEEPSLATE, MoltenTests::moltenLeavesDeepslateUnmelted);
        GooTestFunctions.registerTest(registrar, MOLTEN_LEAVES_NETHERRACK, MoltenTests::moltenLeavesNetherrackUnmelted);
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
     * Two layers of Molten over obsidian heat it red hot and then melt it into
     * a lava source, the cell resting on it at one layer.
     * molten-melts-only-obsidian-magma-and-iron-core-one-layer-each
     * stone-goes-red-hot-before-molten-turns-it-to-lava
     *
     * @param helper the gametest helper
     */
    public static void moltenOverObsidianMeltsItForOneLayer(GameTestHelper helper) {
        meltsForOneLayer(helper, Blocks.OBSIDIAN, Blocks.LAVA);
    }

    /**
     * Two layers of Molten over a magma block melt it into a lava source, the
     * cell resting on it at one layer.
     * molten-melts-only-obsidian-magma-and-iron-core-one-layer-each
     *
     * @param helper the gametest helper
     */
    public static void moltenOverMagmaMeltsItForOneLayer(GameTestHelper helper) {
        meltsForOneLayer(helper, Blocks.MAGMA_BLOCK, Blocks.LAVA);
    }

    /**
     * Two layers of Molten over iron core melt it into a molten iron source,
     * the cell resting on it at one layer.
     * molten-melts-by-a-meltable-group-and-its-parallel-melted-group
     *
     * @param helper the gametest helper
     */
    public static void moltenOverIronCoreMeltsItToMoltenIron(GameTestHelper helper) {
        meltsForOneLayer(helper, GooBlocks.IRON_CORE.get(), GooBlocks.MOLTEN_IRON.get());
    }

    /**
     * Fifteen layers of Molten over stone leave no lava source below them
     * after several times the heat.
     * molten-melts-only-obsidian-magma-and-iron-core-one-layer-each
     *
     * @param helper the gametest helper
     */
    public static void moltenLeavesStoneUnmelted(GameTestHelper helper) {
        leavesUnmelted(helper, Blocks.STONE);
    }

    /**
     * Fifteen layers of Molten over cobblestone leave no lava source below them.
     *
     * @param helper the gametest helper
     */
    public static void moltenLeavesCobblestoneUnmelted(GameTestHelper helper) {
        leavesUnmelted(helper, Blocks.COBBLESTONE);
    }

    /**
     * Fifteen layers of Molten over deepslate leave no lava source below them.
     *
     * @param helper the gametest helper
     */
    public static void moltenLeavesDeepslateUnmelted(GameTestHelper helper) {
        leavesUnmelted(helper, Blocks.DEEPSLATE);
    }

    /**
     * Fifteen layers of Molten over netherrack leave no lava source below them.
     *
     * @param helper the gametest helper
     */
    public static void moltenLeavesNetherrackUnmelted(GameTestHelper helper) {
        leavesUnmelted(helper, Blocks.NETHERRACK);
    }

    // Two layers over the floor block heat it red hot for the heat's ticks, then leave the melted block's source under
    // the cell, one layer left.
    private static void meltsForOneLayer(GameTestHelper helper, Block floor, Block meltedInto) {
        int heatTicks = layMoltenOn(helper, floor, TWO_LAYERS);
        BlockPos cellAt = helper.absolutePos(OVER_THE_FLOOR);
        helper.runAfterDelay(heatTicks / 2, () -> {
            BlockState heating = helper.getBlockState(FLOOR);
            boolean heatStarted = heatsOf(helper).startedAt(helper.getLevel(), cellAt).isPresent();
            helper.assertTrue(heating.is(floor) && heatStarted && holdsLayers(helper, TWO_LAYERS),
                    String.format(MELTED_EARLY, floor, heating, helper.getBlockState(OVER_THE_FLOOR), heatStarted));
        });
        helper.runAfterDelay(heatTicks + PAST_THE_POUR, () -> {
            BlockState melted = helper.getBlockState(FLOOR);
            helper.assertTrue(melted.is(meltedInto) && melted.getFluidState().isSource(),
                    String.format(NOT_MELTED, floor, meltedInto, melted));
            helper.assertTrue(holdsLayers(helper, 1),
                    String.format(LAYERS_LEFT_WRONG, helper.getBlockState(OVER_THE_FLOOR)));
            helper.assertTrue(heatsOf(helper).startedAt(helper.getLevel(), cellAt).isEmpty(), HEAT_LEFT_STANDING);
            helper.succeed();
        });
    }

    // Fifteen layers over the floor block stand several heats, and the block under them is no lava.
    private static void leavesUnmelted(GameTestHelper helper, Block floor) {
        int heatTicks = layMoltenOn(helper, floor, VolumeCellBlock.MAX_LAYERS);
        helper.runAfterDelay(heatTicks * SEVERAL_HEATS, () -> {
            BlockState under = helper.getBlockState(FLOOR);
            helper.assertFalse(under.is(Blocks.LAVA), String.format(MELTED_INTO_LAVA, floor, under));
            helper.succeed();
        });
    }

    // Walls the floor block and the cell over it in stone, so neither lava nor goo runs off, and lays the cell;
    // answers the molten step's heat ticks.
    private static int layMoltenOn(GameTestHelper helper, Block floor, int layers) {
        walledIn(helper, floor);
        for (Direction side : Direction.Plane.HORIZONTAL) {
            helper.setBlock(OVER_THE_FLOOR.relative(side), Blocks.STONE);
        }
        AbilityDefinition molten = AbilityRegistry.of(helper.getLevel()).getAbility(MOLTEN);
        helper.assertTrue(molten != null, ABILITY_REQUIRED);
        helper.setBlock(OVER_THE_FLOOR, GooBlocks.BLAZE_MOLTEN.get().defaultBlockState()
                .setValue(VolumeCellBlock.LAYERS, layers));
        return MoltenStep.in(molten.behaviors()).orElseThrow().heatTicks();
    }

    private static boolean holdsLayers(GameTestHelper helper, int layers) {
        BlockState cell = helper.getBlockState(OVER_THE_FLOOR);
        return cell.is(GooBlocks.BLAZE_MOLTEN.get()) && cell.getValue(VolumeCellBlock.LAYERS) == layers;
    }

    private static MoltenHeats heatsOf(GameTestHelper helper) {
        return GooServerState.of(helper.getLevel().getServer()).moltenHeats();
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
