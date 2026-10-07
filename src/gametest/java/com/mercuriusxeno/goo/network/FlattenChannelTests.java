package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.gametest.KnownRecipes;
import com.mercuriusxeno.goo.item.GooStacks;
import com.mercuriusxeno.goo.registry.GooItems;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Gametest for rock flatten: holding the channel over a dirt mound breaks the
 * mound's blocks above the plane the hold began at, dropping them, and leaves
 * the dirt below the plane and a block outside the flatten tag standing
 * (decision flatten-disc-cursor-breaks-above-the-plane).
 */
public final class FlattenChannelTests {

    private static final BlockPos STAND_POS = new BlockPos(1, 1, 3);
    /** The mound's column, two blocks east of the player. */
    private static final BlockPos MOUND_LOW = STAND_POS.east(2);
    private static final BlockPos MOUND_HIGH = MOUND_LOW.above();
    /** Dirt under the mound, the block the player's plane rests on. */
    private static final BlockPos UNDER_PLANE = MOUND_LOW.below();
    /** Obsidian beside the mound, above the plane but outside the flatten tag. */
    private static final BlockPos OUTSIDE_TAG = MOUND_LOW.north();
    private static final int HELD_GOO = 2;
    private static final double FACE_CENTER = 0.5;
    private static final double ITEM_SEARCH_RADIUS = 3;
    private static final int ASSERT_TICK = 6;
    private static final Identifier ROCK_FLATTEN = Identifier.parse("goo:rock_flatten");
    private static final String ABILITY_REQUIRED = "Ability registry must hold rock_flatten";

    private FlattenChannelTests() {
    }

    /**
     * A mock player holds flatten for four ticks, aiming in turn at the
     * mound's upper block, its lower block, the top of the dirt under it and
     * the obsidian beside it: the two mound blocks break and drop dirt, the
     * dirt under the plane and the obsidian stay.
     *
     * @param helper the gametest helper
     */
    public static void flattenBreaksAboveThePlane(GameTestHelper helper) {
        AbilityDefinition flatten = AbilityRegistry.of(helper.getLevel()).getAbility(ROCK_FLATTEN);
        helper.assertTrue(flatten != null, ABILITY_REQUIRED);
        helper.setBlock(UNDER_PLANE, Blocks.DIRT);
        helper.setBlock(MOUND_LOW, Blocks.DIRT);
        helper.setBlock(MOUND_HIGH, Blocks.DIRT);
        helper.setBlock(OUTSIDE_TAG, Blocks.OBSIDIAN);
        ServerPlayer player = flattener(helper);
        KnownRecipes.teachRequires(player, flatten);
        Vec3[] aims = {westFace(helper, MOUND_HIGH), westFace(helper, MOUND_LOW), topFace(helper, UNDER_PLANE),
                westFace(helper, OUTSIDE_TAG)};
        for (int held = 0; held < aims.length; held++) {
            GooStreamPayload tick = new GooStreamPayload(GooTypes.id(GooTypes.ROCK), ROCK_FLATTEN.toString(),
                    player.getEyePosition(), aims[held], player.getY());
            helper.runAfterDelay(held + 1, () -> GooStreamHandler.streamTick(player, tick));
        }
        helper.runAfterDelay(ASSERT_TICK, () -> {
            helper.getLevel().getServer().getPlayerList().remove(player);
            helper.assertBlockPresent(Blocks.AIR, MOUND_HIGH);
            helper.assertBlockPresent(Blocks.AIR, MOUND_LOW);
            helper.assertBlockPresent(Blocks.DIRT, UNDER_PLANE);
            helper.assertBlockPresent(Blocks.OBSIDIAN, OUTSIDE_TAG);
            helper.assertItemEntityPresent(Items.DIRT, MOUND_LOW, ITEM_SEARCH_RADIUS);
            helper.succeed();
        });
    }

    private static Vec3 westFace(GameTestHelper helper, BlockPos relative) {
        BlockPos pos = helper.absolutePos(relative);
        return new Vec3(pos.getX(), pos.getY() + FACE_CENTER, pos.getZ() + FACE_CENTER);
    }

    private static Vec3 topFace(GameTestHelper helper, BlockPos relative) {
        return Vec3.atCenterOf(helper.absolutePos(relative)).add(0, FACE_CENTER, 0);
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
