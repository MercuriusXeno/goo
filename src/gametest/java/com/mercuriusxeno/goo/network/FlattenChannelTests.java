package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
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

/**
 * Gametest for rock flatten: holding the channel at a dirt mound breaks the
 * cursor's 3x3 from the level remembered from the cursor's block as the hold
 * began up three blocks, one block a tick, top down, dropping them, and leaves
 * that level, the mound above the swath and a block outside the flatten tag
 * standing; begun on a wall's face, it shaves what stands out from the wall
 * and leaves the wall and what is behind it
 * (decision flatten-disc-cursor-breaks-above-the-plane).
 */
public final class FlattenChannelTests {

    private static final BlockPos STAND_POS = new BlockPos(1, 1, 3);
    /** The ground block the cursor rests on as the hold begins: the swath starts above it. */
    private static final BlockPos GROUND = new BlockPos(3, 0, 3);
    /** The block the cursor aims at while held, on the mound above the ground. */
    private static final BlockPos AIMED = GROUND.above();
    /** A stone wall whose west face a hold begins on, with a dirt bump two deep standing out from it. */
    private static final BlockPos WALL = new BlockPos(4, 2, 3);
    /** Dirt behind the wall, on the side its face does not look out to. */
    private static final BlockPos BEHIND_THE_WALL = WALL.east();
    /** Obsidian in the swath's top layer, outside the flatten tag. */
    private static final BlockPos OUTSIDE_TAG = GROUND.above(3).north();
    /** The swath's height above the ground, and a layer past it. */
    private static final int SWATH_HEIGHT = 3;
    private static final int MOUND_HEIGHT = SWATH_HEIGHT + 1;
    /** The 3x3 by 3 is 27 blocks, one a tick, the obsidian passed: thirty ticks clear it. */
    private static final int HOLD_TICKS = 30;
    private static final int HELD_GOO = 3;
    private static final double FACE_CENTER = 0.5;
    private static final double ITEM_SEARCH_RADIUS = 4;
    private static final Identifier ROCK_FLATTEN = Identifier.parse("goo:rock_flatten");
    private static final String ABILITY_REQUIRED = "Ability registry must hold rock_flatten";
    private static final String SHOULD_BREAK_ONE_FROM_THE_TOP = "One tick should break one top-layer block, broke %s";

    private FlattenChannelTests() {
    }

    /**
     * A mock player begins the hold with the cursor on a ground block, then
     * holds flatten at the dirt mound over it: the first tick breaks one block
     * of the swath's top layer, and the hold clears the 3x3 from the ground's
     * top up three blocks, dirt dropping, leaving the ground, the layer above
     * the swath and the obsidian in it.
     *
     * @param helper the gametest helper
     */
    public static void flattenBreaksAboveThePlane(GameTestHelper helper) {
        AbilityDefinition flatten = AbilityRegistry.of(helper.getLevel()).getAbility(ROCK_FLATTEN);
        helper.assertTrue(flatten != null, ABILITY_REQUIRED);
        for (BlockPos column : columns()) {
            for (int y = 0; y <= MOUND_HEIGHT; y++) {
                helper.setBlock(column.above(y), Blocks.DIRT);
            }
        }
        helper.setBlock(OUTSIDE_TAG, Blocks.OBSIDIAN);
        ServerPlayer player = flattener(helper);
        KnownRecipes.teachRequires(player, flatten);
        GooStreamPayload tick = new GooStreamPayload(GooTypes.id(GooTypes.ROCK), ROCK_FLATTEN.toString(),
                player.getEyePosition(), westFace(helper, AIMED), helper.absolutePos(GROUND),
                Direction.UP.get3DDataValue());
        helper.runAfterDelay(1, () -> {
            GooStreamHandler.streamTick(player, tick);
            long broken = columns().stream().map(column -> column.above(SWATH_HEIGHT))
                    .filter(pos -> helper.getBlockState(pos).isAir()).count();
            helper.assertTrue(broken == 1, String.format(SHOULD_BREAK_ONE_FROM_THE_TOP, broken));
        });
        for (int held = 2; held <= HOLD_TICKS; held++) {
            helper.runAfterDelay(held, () -> GooStreamHandler.streamTick(player, tick));
        }
        helper.runAfterDelay(HOLD_TICKS + 1, () -> {
            helper.getLevel().getServer().getPlayerList().remove(player);
            for (BlockPos column : columns()) {
                helper.assertBlockPresent(Blocks.DIRT, column);
                helper.assertBlockPresent(Blocks.DIRT, column.above(MOUND_HEIGHT));
                for (int y = 1; y <= SWATH_HEIGHT; y++) {
                    if (!column.above(y).equals(OUTSIDE_TAG)) {
                        helper.assertBlockPresent(Blocks.AIR, column.above(y));
                    }
                }
            }
            helper.assertBlockPresent(Blocks.OBSIDIAN, OUTSIDE_TAG);
            helper.assertItemEntityPresent(Items.DIRT, AIMED, ITEM_SEARCH_RADIUS);
            helper.succeed();
        });
    }

    /**
     * A mock player facing east begins the hold on the west face of a stone
     * wall and holds flatten at the dirt bump standing out from it: the bump
     * is shaved back to the wall, and the wall and the block behind it stand.
     *
     * @param helper the gametest helper
     */
    public static void flattenShavesAWall(GameTestHelper helper) {
        AbilityDefinition flatten = AbilityRegistry.of(helper.getLevel()).getAbility(ROCK_FLATTEN);
        helper.assertTrue(flatten != null, ABILITY_REQUIRED);
        helper.setBlock(WALL, Blocks.STONE);
        helper.setBlock(BEHIND_THE_WALL, Blocks.DIRT);
        for (int out = 1; out <= 2; out++) {
            helper.setBlock(WALL.west(out), Blocks.DIRT);
        }
        ServerPlayer player = flattener(helper);
        KnownRecipes.teachRequires(player, flatten);
        GooStreamPayload tick = new GooStreamPayload(GooTypes.id(GooTypes.ROCK), ROCK_FLATTEN.toString(),
                player.getEyePosition(), westFace(helper, WALL.west(2)), helper.absolutePos(WALL),
                Direction.WEST.get3DDataValue());
        for (int held = 1; held <= HOLD_TICKS; held++) {
            helper.runAfterDelay(held, () -> GooStreamHandler.streamTick(player, tick));
        }
        helper.runAfterDelay(HOLD_TICKS + 1, () -> {
            helper.getLevel().getServer().getPlayerList().remove(player);
            helper.assertBlockPresent(Blocks.AIR, WALL.west());
            helper.assertBlockPresent(Blocks.AIR, WALL.west(2));
            helper.assertBlockPresent(Blocks.STONE, WALL);
            helper.assertBlockPresent(Blocks.DIRT, BEHIND_THE_WALL);
            helper.succeed();
        });
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

    @SuppressWarnings("removal") // vanilla marks the mock server player helper for removal and names no replacement
    private static ServerPlayer flattener(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Vec3 stand = Vec3.atBottomCenterOf(helper.absolutePos(STAND_POS));
        player.setPos(stand.x, stand.y, stand.z);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(GooItems.GOO_GLOVE.get()));
        player.getInventory().add(GooStacks.createForOutput(GooTypes.ROCK, HELD_GOO * GooStacks.THOUSAND));
        return player;
    }
}
