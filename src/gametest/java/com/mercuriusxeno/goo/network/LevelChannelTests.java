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
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;
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
        GooTestFunctions.registerTest(registrar, LEVEL_FILLS_A_MORTAR_HOLE_AND_GATHERS_THE_REST,
                LevelChannelTests::levelFillsAMortarHoleAndGathersTheRest);
        GooTestFunctions.registerTest(registrar, LEVEL_FILLS_A_MORTAR_HOLE_UNDER_A_FULL_CARRY,
                LevelChannelTests::levelFillsAMortarHoleUnderAFullCarry);
        GooTestFunctions.registerTest(registrar, LEVEL_FILLS_A_ONE_LAYER_MORTAR_HOLE,
                LevelChannelTests::levelFillsAOneLayerMortarHole);
        GooTestFunctions.registerTest(registrar, LEVEL_PAVING_SOUNDS_ONCE_A_TICK,
                LevelChannelTests::levelPavingSoundsOnceATick);
    }

    private static final String LEVEL_PAVING_SOUNDS_ONCE_A_TICK = "level_paving_sounds_once_a_tick";
    /** The floor block the sounding test's cursor rests on, the disc's middle. */
    private static final BlockPos SOUNDING_REST = new BlockPos(3, 0, 3);
    /** The 1 layer Mortar hole under the sounding disc's ring. */
    private static final BlockPos SOUNDING_HOLE = SOUNDING_REST.west();
    /** The two air gaps under the sounding disc's ring. */
    private static final List<BlockPos> SOUNDING_GAPS = List.of(SOUNDING_REST.north(), SOUNDING_REST.east());
    private static final int SIX_LAYERS = 6;
    /** How far a heard sound may stand from the spot it should play at, a float's rounding. */
    private static final double SPOT_TOLERANCE = 1e-3;
    private static final String SHOULD_SOUND_ONCE = "The tick should play one %s place sound at %s, it played %s";
    private static final String SHOULD_SOUND_NOTHING = "A tick laying nothing should play no place sound, it played %s";

    /**
     * A place sound the sounding test heard in its bay.
     *
     * @param at     where it played
     * @param sound  what played
     * @param source the source it played through
     */
    private record HeardSound(Vec3 at, SoundEvent sound, SoundSource source) {
    }

    private static final String LEVEL_FILLS_A_MORTAR_HOLE_AND_GATHERS_THE_REST =
            "level_fills_a_mortar_hole_and_gathers_the_rest";
    private static final String LEVEL_FILLS_A_MORTAR_HOLE_UNDER_A_FULL_CARRY =
            "level_fills_a_mortar_hole_under_a_full_carry";
    private static final String LEVEL_FILLS_A_ONE_LAYER_MORTAR_HOLE = "level_fills_a_one_layer_mortar_hole";
    /** The floor block holding the Mortar hole, the disc's middle when the disc reaches it. */
    private static final BlockPos MORTAR_HOLE = new BlockPos(4, 0, 4);
    /** Where the hole tests' player stands, off every disc they hold. */
    private static final BlockPos HOLE_FILLER_STAND = new BlockPos(3, 1, 1);
    /** The floor block the full carry test's first rest scoops over, nine full cells over its 3x3. */
    private static final BlockPos FILLING_REST = new BlockPos(1, 0, 4);
    private static final int HOLE_LAYERS = 5;
    private static final int THREE_LAYERS = 3;
    /** The held ticks a test's first rest scoops for before the disc moves over the hole. */
    private static final int FILLING_TICKS = 3;
    private static final int FULL_CARRY = 135;
    private static final String SHOULD_FILL_THE_HOLE = "The Mortar hole should read stone, %s stands there";
    private static final String SHOULD_CARRY = "The hold should carry %d layers, it carries %d";
    private static final String SHOULD_RAISE_THE_REST = "The cell over the hole should hold Mortar of %d layers, %s stands";

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

    /**
     * A mock player with an empty carry holds Level for one tick over a floor
     * with no air gap and one 5 layer Mortar cell in the floor row under the
     * disc's middle: the hole reads stone, the carry holds the other 4 layers,
     * and no Mortar block stands over the disc
     * (decision level-passing-over-mortar-below-picks-it-up).
     *
     * @param helper the gametest helper
     */
    public static void levelFillsAMortarHoleAndGathersTheRest(GameTestHelper helper) {
        ServerPlayer player = holeFiller(helper);
        layMortar(helper, MORTAR_HOLE, HOLE_LAYERS);
        holdOverHoleThen(helper, player, List.of(), () -> {
            assertHoleFilled(helper);
            assertCarries(helper, player, HOLE_LAYERS - 1);
            assertNoMortarOver(helper, MORTAR_HOLE);
        });
    }

    /**
     * A mock player scoops nine full Mortar cells into a carry of 135 layers,
     * then holds Level for one tick over a 5 layer Mortar cell in the floor row
     * under the disc's middle: the hole reads stone, the 4 layers it raised
     * stand as Mortar on the new stone, and the carry still holds 135
     * (decision level-passing-over-mortar-below-picks-it-up).
     *
     * @param helper the gametest helper
     */
    public static void levelFillsAMortarHoleUnderAFullCarry(GameTestHelper helper) {
        ServerPlayer player = holeFiller(helper);
        layMortar(helper, MORTAR_HOLE, HOLE_LAYERS);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                layMortar(helper, FILLING_REST.offset(dx, 1, dz), VolumeCellBlock.MAX_LAYERS);
            }
        }
        holdOverHoleThen(helper, player, List.of(FILLING_REST), () -> {
            assertHoleFilled(helper);
            BlockState raised = helper.getBlockState(MORTAR_HOLE.above());
            helper.assertTrue(raised.is(GooBlocks.ROCK_MORTAR.get())
                            && raised.getValue(VolumeCellBlock.LAYERS) == HOLE_LAYERS - 1,
                    String.format(SHOULD_RAISE_THE_REST, HOLE_LAYERS - 1, raised));
            assertCarries(helper, player, FULL_CARRY);
        });
    }

    /**
     * A mock player carrying 3 layers holds Level for one tick over a 1 layer
     * Mortar cell in the floor row under the disc: the hole reads stone, the
     * carry still holds 3, and no Mortar block stands over the disc
     * (decision level-passing-over-mortar-below-picks-it-up).
     *
     * @param helper the gametest helper
     */
    public static void levelFillsAOneLayerMortarHole(GameTestHelper helper) {
        ServerPlayer player = holeFiller(helper);
        layMortar(helper, MORTAR_HOLE, 1);
        layMortar(helper, FILLING_REST.above(), THREE_LAYERS);
        holdOverHoleThen(helper, player, List.of(FILLING_REST), () -> {
            assertHoleFilled(helper);
            assertCarries(helper, player, THREE_LAYERS);
            assertNoMortarOver(helper, MORTAR_HOLE);
        });
    }

    /**
     * A mock player holds Level over a floor with two air gaps and a 1 layer
     * Mortar hole under the disc and a 6 layer Mortar cell over it: the first
     * held tick plays one stone place sound at the middle of the three stones
     * it laid, the next held tick, laying nothing, plays no place sound, and
     * the tick the hold lapses plays one Mortar place sound at the middle of
     * the cells set down
     * (decision level-paving-and-mortar-set-down-play-one-place-sound-a-tick).
     *
     * @param helper the gametest helper
     */
    public static void levelPavingSoundsOnceATick(GameTestHelper helper) {
        ServerPlayer player = holeFiller(helper);
        layMortar(helper, SOUNDING_HOLE, 1);
        SOUNDING_GAPS.forEach(gap -> helper.setBlock(gap, Blocks.AIR));
        layMortar(helper, SOUNDING_REST.above(), SIX_LAYERS);
        SoundEvent stonePlace = SoundType.STONE.getPlaceSound();
        SoundEvent mortarPlace = GooBlocks.ROCK_MORTAR.get().defaultBlockState()
                .getSoundType(helper.getLevel(), helper.absolutePos(SOUNDING_REST.above()), null).getPlaceSound();
        List<HeardSound> heard = new ArrayList<>();
        AABB bay = helper.getBounds();
        Consumer<PlayLevelSoundEvent.AtPosition> listener = event -> {
            SoundEvent sound = event.getSound().value();
            if (event.getLevel() == helper.getLevel() && bay.contains(event.getPosition())
                    && (sound == stonePlace || sound == mortarPlace)) {
                heard.add(new HeardSound(event.getPosition(), sound, event.getSource()));
            }
        };
        NeoForge.EVENT_BUS.addListener(listener);
        GooStreamPayload tick = levelTick(helper, player, SOUNDING_REST, SOUNDING_REST);
        Vec3 stonesMiddle = middleOf(helper, List.of(SOUNDING_HOLE, SOUNDING_GAPS.get(0), SOUNDING_GAPS.get(1)));
        helper.runAfterDelay(1, () -> {
            heard.clear();
            GooStreamHandler.streamTick(player, tick);
            assertHeardOnce(helper, heard, stonePlace, stonesMiddle);
            heard.clear();
        });
        boolean[] heldTwice = {false};
        helper.runAfterDelay(2, () -> {
            GooStreamHandler.streamTick(player, tick);
            helper.assertTrue(heard.isEmpty(), String.format(SHOULD_SOUND_NOTHING, heard));
            heldTwice[0] = true;
        });
        Vec3 setDownMiddle = middleOf(helper, List.of(SOUNDING_REST.above()));
        helper.succeedWhen(() -> {
            helper.assertTrue(heldTwice[0] && carryOf(helper, player).layers() == 0, "The hold should lapse");
            assertHeardOnce(helper, heard, mortarPlace, setDownMiddle);
            NeoForge.EVENT_BUS.unregister(listener);
            helper.getLevel().getServer().getPlayerList().remove(player);
        });
    }

    private static Vec3 middleOf(GameTestHelper helper, List<BlockPos> cells) {
        return cells.stream().map(cell -> Vec3.atCenterOf(helper.absolutePos(cell)))
                .reduce(Vec3.ZERO, Vec3::add).scale(1.0 / cells.size());
    }

    private static void assertHeardOnce(GameTestHelper helper, List<HeardSound> heard, SoundEvent sound, Vec3 at) {
        boolean once = heard.size() == 1 && heard.getFirst().sound() == sound
                && heard.getFirst().source() == SoundSource.BLOCKS
                && heard.getFirst().at().distanceTo(at) < SPOT_TOLERANCE;
        helper.assertTrue(once, String.format(SHOULD_SOUND_ONCE, sound.location(), at, heard));
    }

    private static ServerPlayer holeFiller(GameTestHelper helper) {
        AbilityDefinition levelAbility = AbilityRegistry.of(helper.getLevel()).getAbility(ROCK_LEVEL);
        helper.assertTrue(levelAbility != null, ABILITY_REQUIRED);
        layStoneFloor(helper);
        ServerPlayer player = leveler(helper, HOLE_FILLER_STAND);
        KnownRecipes.teachRequires(player, levelAbility);
        return player;
    }

    private static void layMortar(GameTestHelper helper, BlockPos cell, int layers) {
        helper.setBlock(cell, GooBlocks.ROCK_MORTAR.get().defaultBlockState().setValue(VolumeCellBlock.LAYERS, layers));
    }

    /**
     * Holds Level at each rest before the hole for a few ticks, then over the
     * hole for one held tick, checks what that tick left, and lets the hold
     * lapse before the test passes.
     *
     * @param helper the gametest helper
     * @param player the holding player
     * @param before the floor blocks the cursor rests on first
     * @param check  the checks the tick over the hole answers
     */
    private static void holdOverHoleThen(GameTestHelper helper, ServerPlayer player, List<BlockPos> before,
                                         Runnable check) {
        BlockPos planeBlock = before.isEmpty() ? MORTAR_HOLE : before.getFirst();
        int held = 0;
        for (BlockPos rest : before) {
            GooStreamPayload tick = levelTick(helper, player, rest, planeBlock);
            for (int step = 0; step < FILLING_TICKS; step++) {
                held++;
                helper.runAfterDelay(held, () -> GooStreamHandler.streamTick(player, tick));
            }
        }
        GooStreamPayload overHole = levelTick(helper, player, MORTAR_HOLE, planeBlock);
        boolean[] checked = {false};
        helper.runAfterDelay(held + 1, () -> {
            GooStreamHandler.streamTick(player, overHole);
            check.run();
            checked[0] = true;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(checked[0] && carryOf(helper, player).layers() == 0, "The hold should lapse");
            helper.getLevel().getServer().getPlayerList().remove(player);
        });
    }

    private static GooStreamPayload levelTick(GameTestHelper helper, ServerPlayer player, BlockPos rest,
                                              BlockPos planeBlock) {
        return new GooStreamPayload(GooTypes.id(GooTypes.ROCK), ROCK_LEVEL.toString(), player.getEyePosition(),
                topFace(helper, rest), helper.absolutePos(planeBlock), Direction.UP.get3DDataValue());
    }

    private static void assertHoleFilled(GameTestHelper helper) {
        helper.assertTrue(helper.getBlockState(MORTAR_HOLE).is(Blocks.STONE),
                String.format(SHOULD_FILL_THE_HOLE, helper.getBlockState(MORTAR_HOLE)));
    }

    private static void assertCarries(GameTestHelper helper, ServerPlayer player, int layers) {
        int carried = carryOf(helper, player).layers();
        helper.assertTrue(carried == layers, String.format(SHOULD_CARRY, layers, carried));
    }

    private static void assertNoMortarOver(GameTestHelper helper, BlockPos rest) {
        Set<BlockPos> disc = overDisc(rest);
        mortarIn(helper, disc::contains).keySet().stream().findFirst().ifPresent(cell -> helper.fail(
                String.format(SHOULD_STAND_NO_MORTAR, helper.getBlockState(cell), cell)));
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
