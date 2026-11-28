package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.block.ability.VolumeCellBlock;
import com.mercuriusxeno.goo.registry.GooBlocks;
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
    }

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
        for (int x = 0; x <= FLOOR_EDGE; x++) {
            for (int z = 0; z <= FLOOR_EDGE; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }
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
