package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.program.MortarCarry;
import com.mercuriusxeno.goo.block.ability.MortarCellBlock;
import com.mercuriusxeno.goo.block.ability.VolumeCellBlock;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.registry.GooServerState;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import com.mercuriusxeno.goo.gametest.KnownRecipes;
import com.mercuriusxeno.goo.item.GooStacks;
import com.mercuriusxeno.goo.registry.GooItems;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;
import com.mercuriusxeno.goo.gametest.GooTestFunctions;
import com.mercuriusxeno.goo.gametest.RegistersGameTests;

/**
 * Gametest for rock's Level: holding the channel at a dirt mound breaks the
 * cursor's 3x3 from the level remembered from the cursor's block as the hold
 * began up three blocks, every block progressing at once and the outer layer
 * clearing first, dropping them, and leaves that level, the mound above the
 * swath and a block outside the level tag standing; begun on a wall's face,
 * it shaves what stands out from the wall and leaves the wall and what is
 * behind it (decisions flatten-disc-cursor-breaks-above-the-plane,
 * flatten-progresses-its-whole-swath-outermost-fastest).
 */
@RegistersGameTests
public final class LevelChannelTests {
    private static final String LEVEL_BREAKS_ABOVE_THE_PLANE = "level_breaks_above_the_plane";
    private static final String LEVEL_CLEARS_THE_OUTER_LAYER_FIRST = "level_clears_the_outer_layer_first";

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, LEVEL_BREAKS_ABOVE_THE_PLANE, LevelChannelTests::levelBreaksAboveThePlane);
        GooTestFunctions.registerTest(registrar, LEVEL_CLEARS_THE_OUTER_LAYER_FIRST,
                LevelChannelTests::levelClearsTheOuterLayerFirst);
        GooTestFunctions.registerTest(registrar, LEVEL_UNDER_MORTAR_BRIDGES_A_GAP_WITH_STONE,
                LevelChannelTests::levelUnderMortarBridgesAGapWithStone);
        GooTestFunctions.registerTest(registrar, LEVEL_CARRIES_MORTAR_AS_NO_BLOCK_UNTIL_LET_GO,
                LevelChannelTests::levelCarriesMortarAsNoBlockUntilLetGo);
        GooTestFunctions.registerTest(registrar, LEVEL_GATHERS_LOOSE_MORTAR_ONTO_ITS_PILE,
                LevelChannelTests::levelGathersLooseMortarOntoItsPile);
    }

    private static final String LEVEL_GATHERS_LOOSE_MORTAR_ONTO_ITS_PILE = "level_gathers_loose_mortar_onto_its_pile";
    /** The 12 layer cell, under the corner column at the lowest x and z of the disc's first rest. */
    private static final BlockPos TWELVE_LAYER_CELL = new BlockPos(0, 1, 2);
    /** The 1 layer cell, outside the first disc, under the same corner column of the disc's last rest. */
    private static final BlockPos ONE_LAYER_CELL = new BlockPos(3, 1, 2);
    /** The floor blocks the gathering drag's cursor rests on: each disc's middle, a block in from the corner cell. */
    private static final List<BlockPos> GATHERING_DRAG = List.of(TWELVE_LAYER_CELL.below().offset(1, 0, 1),
            ONE_LAYER_CELL.below().offset(1, 0, 1));
    private static final int TWELVE_LAYERS = 12;
    private static final String SHOULD_GATHER_ONE_PILE = "One Mortar cell of 13 layers should stand over the corner"
            + " column, the bay holds %s";

    private static final String LEVEL_CARRIES_MORTAR_AS_NO_BLOCK_UNTIL_LET_GO =
            "level_carries_mortar_as_no_block_until_let_go";
    /** The full Mortar cells the carry test lays on the floor, both under the disc's first rest. */
    private static final List<BlockPos> CARRIED_CELLS = List.of(new BlockPos(1, 1, 3), new BlockPos(1, 1, 4));
    /** The floor gaps the carry test's drag passes over. */
    private static final List<BlockPos> CARRY_GAPS = List.of(new BlockPos(3, 0, 3), new BlockPos(4, 0, 2));
    /** The four floor blocks the carry test's cursor rests on in turn. */
    private static final List<BlockPos> CARRY_DRAG = List.of(new BlockPos(1, 0, 3), new BlockPos(2, 0, 3),
            new BlockPos(3, 0, 3), new BlockPos(4, 0, 3));
    /** How far over the plane the carry reaches, as deep as Level's swath. */
    private static final int CARRY_DEPTH = 3;
    private static final String SHOULD_STAND_NO_MORTAR = "No Mortar block should stand while the hold carries it, %s at %s";
    private static final String SHOULD_KEEP_EVERY_LAYER =
            "The carry, %d layers, and the stone paved, %d blocks, should make the %d layers scooped";
    private static final String SHOULD_SET_IT_DOWN = "The Mortar set down over the last disc should hold the %d layers"
            + " carried, it holds %d";
    private static final String SHOULD_STAND_ONLY_OVER_THE_LAST_DISC = "No Mortar should stand off the last disc, %s does";

    private static final String LEVEL_UNDER_MORTAR_BRIDGES_A_GAP_WITH_STONE = "level_under_mortar_bridges_a_gap_with_stone";
    /** Where the paving test's player stands, off the disc's path. */
    private static final BlockPos PAVER_STAND = new BlockPos(1, 1, 1);
    /** The floor's far corner, the floor laid from the bay's corner to it. */
    private static final int FLOOR_EDGE = 5;
    /** The floor block the hold begins on, a full Mortar cell lying on it. */
    private static final BlockPos PAVING_START = new BlockPos(2, 0, 3);
    /** The one block gap in the floor beside the start. */
    private static final BlockPos FLOOR_GAP = PAVING_START.east();
    /** The floor block past the gap, where the drag ends. */
    private static final BlockPos PAVING_END = FLOOR_GAP.east();
    /** The ticks the cursor rests on each block of the drag. */
    private static final int TICKS_PER_DRAG_STEP = 5;
    private static final double TOP_FACE = 1.0;
    private static final String SHOULD_PAVE_THE_GAP = "The gap the disc crossed should hold stone, %s stands there";
    private static final String SHOULD_CARRY_THE_MORTAR = "Mortar should stand over the cursor's last block, %s stands there";


    private static final BlockPos STAND_POS = new BlockPos(1, 1, 3);
    /** The ground block the cursor rests on as the hold begins: the swath starts above it. */
    private static final BlockPos GROUND = new BlockPos(3, 0, 3);
    /** The block the cursor aims at while held, on the mound above the ground. */
    private static final BlockPos AIMED = GROUND.above();
    /** Obsidian in the swath's top layer, outside the level tag. */
    private static final BlockPos OUTSIDE_TAG = GROUND.above(3).north();
    /** The swath's height above the ground, and a layer past it. */
    private static final int SWATH_HEIGHT = 3;
    private static final int MOUND_HEIGHT = SWATH_HEIGHT + 1;
    /** The longest hold a test streams while it polls for the swath to clear, inside its timeout. */
    private static final int MOST_HOLD_TICKS = 55;
    /** The two layers of a shallow mound: the outer one and the one beneath it, touching the ground. */
    private static final int OUTER_LAYER = 2;
    private static final int BENEATH_LAYER = 1;
    private static final int NOT_YET = 0;
    private static final int HELD_GOO = 3;
    private static final double FACE_CENTER = 0.5;
    private static final double ITEM_SEARCH_RADIUS = 4;
    private static final Identifier ROCK_LEVEL = Identifier.parse("goo:rock_level");
    private static final String ABILITY_REQUIRED = "Ability registry must hold rock_level";
    private static final String SHOULD_STAND_AFTER_ONE_TICK = "Every block should stand after one tick, %s is air";
    private static final String SHOULD_CLEAR_THE_OUTER_LAYER_FIRST =
            "The outer layer should be air before the layer beneath breaks: outer air on tick %d, beneath broke on %d";
    private static final String SHOULD_CLEAR_BOTH_LAYERS = "Both layers should clear while held: outer air on tick %d, beneath broke on %d";

    private LevelChannelTests() {
    }

    /**
     * A mock player begins the hold with the cursor on a ground block, then
     * holds Level at the dirt mound over it: the hold, polled until it is
     * done, clears the 3x3 from the ground's top up three blocks, dirt dropping.
     *
     * @param helper the gametest helper
     */
    public static void levelBreaksAboveThePlane(GameTestHelper helper) {
        AbilityDefinition levelAbility = AbilityRegistry.of(helper.getLevel()).getAbility(ROCK_LEVEL);
        helper.assertTrue(levelAbility != null, ABILITY_REQUIRED);
        for (BlockPos column : columns()) {
            for (int y = 0; y <= MOUND_HEIGHT; y++) {
                helper.setBlock(column.above(y), Blocks.DIRT);
            }
        }
        helper.setBlock(OUTSIDE_TAG, Blocks.OBSIDIAN);
        ServerPlayer player = leveler(helper);
        KnownRecipes.teachRequires(player, levelAbility);
        GooStreamPayload tick = new GooStreamPayload(GooTypes.id(GooTypes.ROCK), ROCK_LEVEL.toString(),
                player.getEyePosition(), westFace(helper, AIMED), helper.absolutePos(GROUND),
                Direction.UP.get3DDataValue());
        for (int held = 1; held <= MOST_HOLD_TICKS; held++) {
            helper.runAfterDelay(held, () -> GooStreamHandler.streamTick(player, tick));
        }
        helper.succeedWhen(() -> {
            for (BlockPos column : columns()) {
                for (int y = 1; y <= SWATH_HEIGHT; y++) {
                    if (!column.above(y).equals(OUTSIDE_TAG)) {
                        helper.assertBlockPresent(Blocks.AIR, column.above(y));
                    }
                }
            }
            helper.assertItemEntityPresent(Items.DIRT, AIMED, ITEM_SEARCH_RADIUS);
            helper.getLevel().getServer().getPlayerList().remove(player);
        });
    }

    /**
     * A mock player holds Level over a dirt mound two layers deep: after one
     * tick every block stands, and the whole outer layer is air on an earlier
     * tick than any block of the layer beneath it breaks
     * (decision flatten-progresses-its-whole-swath-outermost-fastest).
     *
     * @param helper the gametest helper
     */
    public static void levelClearsTheOuterLayerFirst(GameTestHelper helper) {
        AbilityDefinition levelAbility = AbilityRegistry.of(helper.getLevel()).getAbility(ROCK_LEVEL);
        helper.assertTrue(levelAbility != null, ABILITY_REQUIRED);
        for (BlockPos column : columns()) {
            for (int y = 0; y <= OUTER_LAYER; y++) {
                helper.setBlock(column.above(y), Blocks.DIRT);
            }
        }
        ServerPlayer player = leveler(helper);
        KnownRecipes.teachRequires(player, levelAbility);
        GooStreamPayload tick = new GooStreamPayload(GooTypes.id(GooTypes.ROCK), ROCK_LEVEL.toString(),
                player.getEyePosition(), westFace(helper, AIMED), helper.absolutePos(GROUND),
                Direction.UP.get3DDataValue());
        int[] outerAirOn = {NOT_YET};
        int[] beneathBrokeOn = {NOT_YET};
        helper.runAfterDelay(1, () -> {
            GooStreamHandler.streamTick(player, tick);
            for (BlockPos column : columns()) {
                for (int y = BENEATH_LAYER; y <= OUTER_LAYER; y++) {
                    helper.assertTrue(!helper.getBlockState(column.above(y)).isAir(),
                            String.format(SHOULD_STAND_AFTER_ONE_TICK, column.above(y)));
                }
            }
        });
        for (int held = 2; held <= MOST_HOLD_TICKS; held++) {
            int at = held;
            helper.runAfterDelay(held, () -> {
                GooStreamHandler.streamTick(player, tick);
                if (outerAirOn[0] == NOT_YET && layerAir(helper, OUTER_LAYER)) {
                    outerAirOn[0] = at;
                }
                if (beneathBrokeOn[0] == NOT_YET && columns().stream()
                        .anyMatch(column -> helper.getBlockState(column.above(BENEATH_LAYER)).isAir())) {
                    beneathBrokeOn[0] = at;
                }
            });
        }
        helper.succeedWhen(() -> {
            helper.assertTrue(outerAirOn[0] != NOT_YET && beneathBrokeOn[0] != NOT_YET,
                    String.format(SHOULD_CLEAR_BOTH_LAYERS, outerAirOn[0], beneathBrokeOn[0]));
            helper.assertTrue(beneathBrokeOn[0] > outerAirOn[0],
                    String.format(SHOULD_CLEAR_THE_OUTER_LAYER_FIRST, outerAirOn[0], beneathBrokeOn[0]));
            helper.getLevel().getServer().getPlayerList().remove(player);
        });
    }

    /**
     * A mock player begins the hold on a floor block under a full Mortar cell
     * and drags Level's disc east across a one block gap in the floor and onto
     * the block past it: the gap holds stone, no goo item lies about, and the
     * Mortar stands over the block the cursor last rested on
     * (decision level-dragged-under-rock-goo-carries-it-and-deposits-stone-under-the-disc).
     *
     * @param helper the gametest helper
     */
    public static void levelUnderMortarBridgesAGapWithStone(GameTestHelper helper) {
        AbilityDefinition levelAbility = AbilityRegistry.of(helper.getLevel()).getAbility(ROCK_LEVEL);
        helper.assertTrue(levelAbility != null, ABILITY_REQUIRED);
        layStoneFloor(helper);
        helper.setBlock(FLOOR_GAP, Blocks.AIR);
        helper.setBlock(PAVING_START.above(), GooBlocks.ROCK_MORTAR.get().defaultBlockState()
                .setValue(VolumeCellBlock.LAYERS, VolumeCellBlock.MAX_LAYERS));
        ServerPlayer player = leveler(helper, PAVER_STAND);
        KnownRecipes.teachRequires(player, levelAbility);
        List<BlockPos> drag = List.of(PAVING_START, FLOOR_GAP, PAVING_END);
        int held = 0;
        for (BlockPos rest : drag) {
            GooStreamPayload tick = new GooStreamPayload(GooTypes.id(GooTypes.ROCK), ROCK_LEVEL.toString(),
                    player.getEyePosition(), topFace(helper, rest), helper.absolutePos(PAVING_START),
                    Direction.UP.get3DDataValue());
            for (int step = 0; step < TICKS_PER_DRAG_STEP; step++) {
                held++;
                helper.runAfterDelay(held, () -> GooStreamHandler.streamTick(player, tick));
            }
        }
        boolean[] dragged = {false};
        helper.runAfterDelay(held + 1, () -> dragged[0] = true);
        helper.succeedWhen(() -> {
            helper.assertTrue(dragged[0], "The drag should finish");
            helper.assertTrue(helper.getBlockState(FLOOR_GAP).is(Blocks.STONE),
                    String.format(SHOULD_PAVE_THE_GAP, helper.getBlockState(FLOOR_GAP)));
            helper.assertTrue(helper.getBlockState(PAVING_END.above()).is(GooBlocks.ROCK_MORTAR.get()),
                    String.format(SHOULD_CARRY_THE_MORTAR, helper.getBlockState(PAVING_END.above())));
            helper.assertItemEntityNotPresent(GooItems.GOO.get(), PAVING_START, ITEM_SEARCH_RADIUS);
            helper.getLevel().getServer().getPlayerList().remove(player);
        });
    }

    /**
     * A mock player holds Level under two full Mortar cells and drags the
     * cursor across four floor blocks over two gaps: on every held tick no
     * Mortar block stands anywhere in the bay, and the layers the hold carries
     * and the stone it paved make the thirty it scooped; once the hold lapses,
     * the Mortar over the last disc holds every carried layer and none stands
     * elsewhere (decision carried-mortar-stops-being-a-block-on-the-level-plane).
     *
     * @param helper the gametest helper
     */
    public static void levelCarriesMortarAsNoBlockUntilLetGo(GameTestHelper helper) {
        AbilityDefinition levelAbility = AbilityRegistry.of(helper.getLevel()).getAbility(ROCK_LEVEL);
        helper.assertTrue(levelAbility != null, ABILITY_REQUIRED);
        layStoneFloor(helper);
        CARRY_GAPS.forEach(gap -> helper.setBlock(gap, Blocks.AIR));
        CARRIED_CELLS.forEach(cell -> helper.setBlock(cell, GooBlocks.ROCK_MORTAR.get().defaultBlockState()
                .setValue(VolumeCellBlock.LAYERS, VolumeCellBlock.MAX_LAYERS)));
        int scooped = CARRIED_CELLS.size() * VolumeCellBlock.MAX_LAYERS;
        ServerPlayer player = leveler(helper, PAVER_STAND);
        KnownRecipes.teachRequires(player, levelAbility);
        int held = 0;
        int[] carriedAtLastTick = {0};
        for (BlockPos rest : CARRY_DRAG) {
            GooStreamPayload tick = new GooStreamPayload(GooTypes.id(GooTypes.ROCK), ROCK_LEVEL.toString(),
                    player.getEyePosition(), topFace(helper, rest), helper.absolutePos(CARRY_DRAG.getFirst()),
                    Direction.UP.get3DDataValue());
            for (int step = 0; step < TICKS_PER_DRAG_STEP; step++) {
                held++;
                helper.runAfterDelay(held, () -> {
                    GooStreamHandler.streamTick(player, tick);
                    mortarIn(helper, cell -> true).keySet().stream().findFirst().ifPresent(cell -> helper.fail(
                            String.format(SHOULD_STAND_NO_MORTAR, helper.getBlockState(cell), cell)));
                    int carried = carryOf(helper, player).layers();
                    int paved = (int) CARRY_GAPS.stream().filter(gap -> helper.getBlockState(gap).is(Blocks.STONE))
                            .count();
                    helper.assertTrue(carried + paved == scooped,
                            String.format(SHOULD_KEEP_EVERY_LAYER, carried, paved, scooped));
                    carriedAtLastTick[0] = carried;
                });
            }
        }
        boolean[] dragged = {false};
        helper.runAfterDelay(held + 1, () -> dragged[0] = true);
        Set<BlockPos> lastDisc = overDisc(CARRY_DRAG.getLast());
        helper.succeedWhen(() -> {
            helper.assertTrue(dragged[0] && carryOf(helper, player).layers() == 0, "The hold should lapse");
            int setDown = mortarIn(helper, lastDisc::contains).values().stream().mapToInt(Integer::intValue).sum();
            helper.assertTrue(setDown == carriedAtLastTick[0],
                    String.format(SHOULD_SET_IT_DOWN, carriedAtLastTick[0], setDown));
            mortarIn(helper, cell -> !lastDisc.contains(cell)).keySet().stream().findFirst()
                    .ifPresent(cell -> helper.fail(String.format(SHOULD_STAND_ONLY_OVER_THE_LAST_DISC, cell)));
            helper.getLevel().getServer().getPlayerList().remove(player);
        });
    }

    /**
     * A mock player holds Level with a 12 layer Mortar cell under a corner
     * column of the disc and moves the cursor on until a 1 layer cell beyond
     * the first disc lies under that same corner column, over a floor with no
     * gap: once the hold lapses, one Mortar cell of 13 layers stands over that
     * corner column and no other Mortar stands
     * (decision level-gathers-loose-mortar-into-the-closest-empty-slot).
     *
     * @param helper the gametest helper
     */
    public static void levelGathersLooseMortarOntoItsPile(GameTestHelper helper) {
        AbilityDefinition levelAbility = AbilityRegistry.of(helper.getLevel()).getAbility(ROCK_LEVEL);
        helper.assertTrue(levelAbility != null, ABILITY_REQUIRED);
        layStoneFloor(helper);
        helper.setBlock(TWELVE_LAYER_CELL, GooBlocks.ROCK_MORTAR.get().defaultBlockState()
                .setValue(VolumeCellBlock.LAYERS, TWELVE_LAYERS));
        helper.setBlock(ONE_LAYER_CELL, GooBlocks.ROCK_MORTAR.get().defaultBlockState()
                .setValue(VolumeCellBlock.LAYERS, 1));
        // the 13 layer cell set down pours into open neighbours a tick on, which a poll a tick late would miss
        for (Direction side : Direction.Plane.HORIZONTAL) {
            helper.setBlock(ONE_LAYER_CELL.relative(side), Blocks.OBSIDIAN);
        }
        ServerPlayer player = leveler(helper, PAVER_STAND);
        KnownRecipes.teachRequires(player, levelAbility);
        int held = 0;
        for (BlockPos rest : GATHERING_DRAG) {
            GooStreamPayload tick = new GooStreamPayload(GooTypes.id(GooTypes.ROCK), ROCK_LEVEL.toString(),
                    player.getEyePosition(), topFace(helper, rest), helper.absolutePos(GATHERING_DRAG.getFirst()),
                    Direction.UP.get3DDataValue());
            for (int step = 0; step < TICKS_PER_DRAG_STEP; step++) {
                held++;
                helper.runAfterDelay(held, () -> GooStreamHandler.streamTick(player, tick));
            }
        }
        boolean[] dragged = {false};
        helper.runAfterDelay(held + 1, () -> dragged[0] = true);
        helper.succeedWhen(() -> {
            helper.assertTrue(dragged[0] && carryOf(helper, player).layers() == 0, "The hold should lapse");
            Map<BlockPos, Integer> standing = mortarIn(helper, cell -> true);
            helper.assertTrue(standing.equals(Map.of(ONE_LAYER_CELL, TWELVE_LAYERS + 1)),
                    String.format(SHOULD_GATHER_ONE_PILE, standing));
            helper.getLevel().getServer().getPlayerList().remove(player);
        });
    }

    private static void layStoneFloor(GameTestHelper helper) {
        for (int x = 0; x <= FLOOR_EDGE; x++) {
            for (int z = 0; z <= FLOOR_EDGE; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }
    }

    private static MortarCarry carryOf(GameTestHelper helper, ServerPlayer player) {
        return GooServerState.of(helper.getLevel().getServer()).streamHolds().marks(player.getUUID()).mortarCarry();
    }

    /**
     * The Mortar standing over the bay's floor, each cell with its layers.
     *
     * @param helper the gametest helper
     * @param within which cells, relative to the bay, to read
     * @return the cells holding Mortar, relative to the bay, each with its layers
     */
    private static Map<BlockPos, Integer> mortarIn(GameTestHelper helper, Predicate<BlockPos> within) {
        Map<BlockPos, Integer> mortar = new LinkedHashMap<>();
        for (int x = 0; x <= FLOOR_EDGE; x++) {
            for (int y = 0; y <= CARRY_DEPTH; y++) {
                for (int z = 0; z <= FLOOR_EDGE; z++) {
                    BlockPos cell = new BlockPos(x, y, z);
                    if (within.test(cell) && helper.getBlockState(cell).is(GooBlocks.ROCK_MORTAR.get())) {
                        mortar.put(cell, MortarCellBlock.layersAt(helper.getLevel(), helper.absolutePos(cell)));
                    }
                }
            }
        }
        return mortar;
    }

    private static Set<BlockPos> overDisc(BlockPos rest) {
        Set<BlockPos> cells = new HashSet<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = 1; dy <= CARRY_DEPTH; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    cells.add(rest.offset(dx, dy, dz));
                }
            }
        }
        return cells;
    }

    private static Vec3 topFace(GameTestHelper helper, BlockPos relative) {
        BlockPos pos = helper.absolutePos(relative);
        return new Vec3(pos.getX() + FACE_CENTER, pos.getY() + TOP_FACE, pos.getZ() + FACE_CENTER);
    }

    private static boolean layerAir(GameTestHelper helper, int up) {
        return columns().stream().allMatch(column -> helper.getBlockState(column.above(up)).isAir());
    }

    /**
     * The swath's nine columns at the ground's height.
     *
     * @return the ground blocks of the 3x3
     */
    private static List<BlockPos> columns() {
        List<BlockPos> columns = new ArrayList<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                columns.add(GROUND.offset(dx, 0, dz));
            }
        }
        return columns;
    }

    private static Vec3 westFace(GameTestHelper helper, BlockPos relative) {
        BlockPos pos = helper.absolutePos(relative);
        return new Vec3(pos.getX(), pos.getY() + FACE_CENTER, pos.getZ() + FACE_CENTER);
    }

    private static ServerPlayer leveler(GameTestHelper helper) {
        return leveler(helper, STAND_POS);
    }

    @SuppressWarnings("removal") // vanilla marks the mock server player helper for removal and names no replacement
    private static ServerPlayer leveler(GameTestHelper helper, BlockPos standOn) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Vec3 stand = Vec3.atBottomCenterOf(helper.absolutePos(standOn));
        player.setPos(stand.x, stand.y, stand.z);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(GooItems.GOO_GLOVE.get()));
        player.getInventory().add(GooStacks.createForOutput(GooTypes.ROCK, HELD_GOO * GooStacks.THOUSAND));
        return player;
    }
}
