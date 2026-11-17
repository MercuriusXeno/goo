package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.program.MoltenStep;
import com.mercuriusxeno.goo.block.ability.MoltenHeats;
import com.mercuriusxeno.goo.block.ability.SideCoat;
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
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.List;
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
    private static final String MOLTEN_COOKS_COBBLESTONE = "molten_over_cobblestone_cooks_it_to_smooth_stone";
    private static final String MOLTEN_COOKS_AN_OAK_LOG = "molten_over_an_oak_log_cooks_it_to_charcoal";
    private static final String MOLTEN_PERSISTS_ON_DIRT = "molten_over_dirt_persists_whole";
    /** The layers the cobblestone test lays, one for each of its two cook steps and one left. */
    private static final int THREE_LAYERS = 3;
    /** The heats the cobblestone test waits out, one for each layer it lays. */
    private static final int THREE_HEATS = 3;
    private static final String NOT_COOKED_TO_SMOOTH_STONE = "Three layers of Molten should cook cobblestone to"
            + " smooth stone under one layer, stands %s under %s";
    private static final String NOT_COOKED_TO_CHARCOAL = "Two layers of Molten should cook the oak log to one"
            + " charcoal and keep one layer, stands %s with %s charcoal and %s layers";
    private static final String DIRT_SPENT_GOO = "Fifteen layers of Molten should stand whole on dirt, stands %s"
            + " under %s";
    private static final String MOLTEN_CLINGS_TO_A_WALL = "molten_drip_on_a_wall_attaches_to_it";
    private static final String MOLTEN_CLINGS_TO_A_CEILING = "molten_drip_under_a_ceiling_attaches_to_it";
    private static final String MOLTEN_PILES_ITS_STEP = "molten_stands_its_step_over_an_open_neighbour";
    /** A stone block hung in open air, two blocks over the floor, whose west face and underside drips strike. */
    private static final BlockPos HUNG_BLOCK = new BlockPos(3, 4, 2);
    /** The open cell west of the hung block, beside its struck face. */
    private static final BlockPos BESIDE_THE_HUNG_BLOCK = HUNG_BLOCK.west();
    /** The open cell under the hung block, below its struck underside. */
    private static final BlockPos UNDER_THE_HUNG_BLOCK = HUNG_BLOCK.below();
    /** The open cell east of the pile test's tall cell, the two walled in on every other side. */
    private static final BlockPos BESIDE_THE_FLOOR = OVER_THE_FLOOR.east();
    /** The layers past Molten's step the pile test's tall cell is raised by, so it pours. */
    private static final int PAST_THE_STEP = 4;
    /** The layers an open neighbour takes from a cell standing four layers past its step. */
    private static final int TAKEN_PAST_THE_STEP = 2;
    /** Ticks a pile is watched, past several of a blaze cell's waits between pours. */
    private static final int PILE_SETTLE_TICKS = PAST_THE_POUR * 3;
    private static final String NOT_ON_THE_WALL = "A Molten drip striking a wall's side should leave a cell attached"
            + " to it, stands %s";
    private static final String NOT_ON_THE_CEILING = "A Molten drip striking a block's underside should leave a cell"
            + " attached to that ceiling, stands %s";
    private static final String NO_STEP = "Molten should pile a step over its neighbour, its cohesion reads %d";
    private static final String STEP_POURED ="A Molten cell %d layers over an open neighbour should hold them,"
            + " holds %d beside %d";
    private static final String STEP_NOT_POURED = "A Molten cell %d layers over an open neighbour should pour"
            + " until it holds %d beside %d, holds %d beside %d";

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
        GooTestFunctions.registerTest(registrar, MOLTEN_COOKS_COBBLESTONE, MoltenTests::moltenOverCobblestoneCooksItToSmoothStone);
        GooTestFunctions.registerTest(registrar, MOLTEN_COOKS_AN_OAK_LOG, MoltenTests::moltenOverAnOakLogCooksItToCharcoal);
        GooTestFunctions.registerTest(registrar, MOLTEN_PERSISTS_ON_DIRT, MoltenTests::moltenOverDirtPersistsWhole);
        GooTestFunctions.registerTest(registrar, MOLTEN_CLINGS_TO_A_WALL, MoltenTests::moltenDripOnAWallAttachesToIt);
        GooTestFunctions.registerTest(registrar, MOLTEN_CLINGS_TO_A_CEILING,
                MoltenTests::moltenDripUnderACeilingAttachesToIt);
        GooTestFunctions.registerTest(registrar, MOLTEN_PILES_ITS_STEP,
                MoltenTests::moltenStandsItsStepOverAnOpenNeighbour);
    }

    /**
     * One Molten drip striking the west face of a stone block hung in open
     * air leaves a blaze cell beside that face, attached to it, since
     * Molten's adhesion holds an underside and every rung below it.
     * blaze-goo-is-adhesion-four-and-cohesion-five
     * drip-striking-a-side-sticks-or-runs-by-adhesion
     *
     * @param helper the gametest helper
     */
    public static void moltenDripOnAWallAttachesToIt(GameTestHelper helper) {
        hangABlock(helper);
        landOneDripOnSide(helper, Direction.WEST);
        BlockState cell = helper.getBlockState(BESIDE_THE_HUNG_BLOCK);
        boolean attached = cell.is(GooBlocks.BLAZE_MOLTEN.get())
                && VolumeCellBlock.coatOn(cell, Direction.EAST) == SideCoat.ATTACHED;
        helper.assertTrue(attached, String.format(NOT_ON_THE_WALL, cell));
        helper.succeed();
    }

    /**
     * One Molten drip striking the underside of a stone block hung in open
     * air leaves a blaze cell under it, attached to that ceiling.
     * blaze-goo-is-adhesion-four-and-cohesion-five
     * drip-striking-a-ceiling-runs-down-a-wall-or-drops
     *
     * @param helper the gametest helper
     */
    public static void moltenDripUnderACeilingAttachesToIt(GameTestHelper helper) {
        hangABlock(helper);
        landOneDripOnSide(helper, Direction.DOWN);
        BlockState cell = helper.getBlockState(UNDER_THE_HUNG_BLOCK);
        boolean attached = cell.is(GooBlocks.BLAZE_MOLTEN.get()) && cell.getValue(VolumeCellBlock.CEILING);
        helper.assertTrue(attached, String.format(NOT_ON_THE_CEILING, cell));
        helper.succeed();
    }

    /**
     * A Molten cell standing Molten's step in layers over an open dirt
     * neighbour, the two walled in on every other side, holds every layer
     * while its pours come due; raised four layers past the step, it pours
     * until it stands the step over the neighbour again, two layers having
     * crossed. Molten's step is read from the goo type registry, since its
     * cohesion is dialed by feel.
     * blaze-goo-is-adhesion-four-and-cohesion-five
     * cohesion-is-the-step-the-goo-holds
     *
     * @param helper the gametest helper
     */
    public static void moltenStandsItsStepOverAnOpenNeighbour(GameTestHelper helper) {
        int step = helper.getLevel().registryAccess().get(GooTypes.BLAZE).map(type -> type.value().cohesion())
                .orElse(0);
        helper.assertTrue(step > 0, String.format(NO_STEP, step));
        helper.setBlock(FLOOR, Blocks.DIRT);
        helper.setBlock(FLOOR.east(), Blocks.DIRT);
        for (Direction side : List.of(Direction.NORTH, Direction.SOUTH)) {
            helper.setBlock(OVER_THE_FLOOR.relative(side), Blocks.STONE);
            helper.setBlock(BESIDE_THE_FLOOR.relative(side), Blocks.STONE);
        }
        helper.setBlock(OVER_THE_FLOOR.west(), Blocks.STONE);
        helper.setBlock(BESIDE_THE_FLOOR.east(), Blocks.STONE);
        helper.setBlock(BESIDE_THE_FLOOR, Blocks.AIR);
        helper.setBlock(OVER_THE_FLOOR, moltenOf(step));
        helper.runAfterDelay(PILE_SETTLE_TICKS, () -> {
            int tall = layersAt(helper, OVER_THE_FLOOR);
            int beside = layersAt(helper, BESIDE_THE_FLOOR);
            helper.assertTrue(tall == step && beside == 0, String.format(STEP_POURED, step, tall, beside));
            helper.setBlock(OVER_THE_FLOOR, moltenOf(step + PAST_THE_STEP));
        });
        helper.runAfterDelay(PILE_SETTLE_TICKS * 2, () -> {
            int tall = layersAt(helper, OVER_THE_FLOOR);
            int beside = layersAt(helper, BESIDE_THE_FLOOR);
            int heldTall = step + PAST_THE_STEP - TAKEN_PAST_THE_STEP;
            helper.assertTrue(tall == heldTall && beside == TAKEN_PAST_THE_STEP, String.format(STEP_NOT_POURED,
                    step + PAST_THE_STEP, heldTall, TAKEN_PAST_THE_STEP, tall, beside));
            helper.succeed();
        });
    }

    private static BlockState moltenOf(int layers) {
        return GooBlocks.BLAZE_MOLTEN.get().defaultBlockState().setValue(VolumeCellBlock.LAYERS, layers);
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

    // Lands one Molten drip on a face of the hung block from a blaze-holding caster, who pays for its layer.
    private static void landOneDripOnSide(GameTestHelper helper, Direction face) {
        AbilityDefinition molten = AbilityRegistry.of(helper.getLevel()).getAbility(MOLTEN);
        helper.assertTrue(molten != null, ABILITY_REQUIRED);
        ServerPlayer caster = SelfDeliveryTests.invoker(helper, GooTypes.BLAZE);
        KnownRecipes.teachRequires(caster, molten);
        LandedDrips.landOnSide(helper, caster, molten, HUNG_BLOCK, face, 1);
        helper.getLevel().getServer().getPlayerList().remove(caster);
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

    /**
     * Three layers of Molten over cobblestone cook it to stone for one layer,
     * then the stone to smooth stone for a second, and smooth stone, having
     * no furnace result, spends the last layer on nothing.
     * molten-cooks-the-block-below-it-as-flames-cooks-blocks
     *
     * @param helper the gametest helper
     */
    public static void moltenOverCobblestoneCooksItToSmoothStone(GameTestHelper helper) {
        int heatTicks = layMoltenOn(helper, Blocks.COBBLESTONE, THREE_LAYERS);
        helper.runAfterDelay(heatTicks * THREE_HEATS + PAST_THE_POUR, () -> {
            BlockState cooked = helper.getBlockState(FLOOR);
            helper.assertTrue(cooked.is(Blocks.SMOOTH_STONE) && holdsLayers(helper, 1),
                    String.format(NOT_COOKED_TO_SMOOTH_STONE, cooked, helper.getBlockState(OVER_THE_FLOOR)));
            helper.succeed();
        });
    }

    /**
     * Two layers of Molten over an oak log cook it as Flames does into its
     * furnace result, an item: the log is gone, one charcoal rests where it
     * stood, and one layer is spent, the other fallen into the log's space.
     * The log stands on dirt, which cooks into nothing, so the fallen layer
     * stays.
     * molten-cooks-the-block-below-it-as-flames-cooks-blocks
     *
     * @param helper the gametest helper
     */
    public static void moltenOverAnOakLogCooksItToCharcoal(GameTestHelper helper) {
        int heatTicks = layMoltenOn(helper, Blocks.OAK_LOG, TWO_LAYERS);
        helper.setBlock(FLOOR.below(), Blocks.DIRT);
        helper.runAfterDelay(heatTicks + PAST_THE_POUR, () -> {
            BlockState log = helper.getBlockState(FLOOR);
            AABB logSpace = new AABB(helper.absolutePos(FLOOR));
            List<ItemEntity> charcoal = helper.getLevel().getEntitiesOfClass(ItemEntity.class, logSpace,
                    item -> item.getItem().is(Items.CHARCOAL));
            int charcoalCount = charcoal.stream().mapToInt(item -> item.getItem().getCount()).sum();
            int layers = layersAt(helper, FLOOR) + layersAt(helper, OVER_THE_FLOOR);
            helper.assertTrue(!log.is(Blocks.OAK_LOG) && charcoalCount == 1 && layers == 1,
                    String.format(NOT_COOKED_TO_CHARCOAL, log, charcoalCount, layers));
            helper.succeed();
        });
    }

    /**
     * Fifteen layers of Molten over dirt, which neither melts nor cooks,
     * stand whole after several heats.
     * molten-cooks-the-block-below-it-as-flames-cooks-blocks
     * placed-goo-persists-and-piles-high
     *
     * @param helper the gametest helper
     */
    public static void moltenOverDirtPersistsWhole(GameTestHelper helper) {
        int heatTicks = layMoltenOn(helper, Blocks.DIRT, VolumeCellBlock.MAX_LAYERS);
        helper.runAfterDelay(heatTicks * SEVERAL_HEATS, () -> {
            BlockState under = helper.getBlockState(FLOOR);
            helper.assertTrue(under.is(Blocks.DIRT) && holdsLayers(helper, VolumeCellBlock.MAX_LAYERS),
                    String.format(DIRT_SPENT_GOO, under, helper.getBlockState(OVER_THE_FLOOR)));
            helper.succeed();
        });
    }

    private static int layersAt(GameTestHelper helper, BlockPos pos) {
        BlockState state = helper.getBlockState(pos);
        return state.is(GooBlocks.BLAZE_MOLTEN.get()) ? state.getValue(VolumeCellBlock.LAYERS) : 0;
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
