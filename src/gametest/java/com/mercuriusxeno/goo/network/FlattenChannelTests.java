package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.program.ChannelAim;
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
 * Gametest for rock flatten: holding the channel at a dirt mound breaks the
 * cursor's 3x3 above the plane remembered from the cursor's block as the hold
 * began, dropping them, and leaves that block's level and a block outside the
 * flatten tag standing
 * (decision flatten-disc-cursor-breaks-above-the-plane).
 */
public final class FlattenChannelTests {

    private static final BlockPos STAND_POS = new BlockPos(1, 1, 3);
    /** The ground block the cursor rests on as the hold begins: the plane is its top. */
    private static final BlockPos GROUND = new BlockPos(3, 0, 3);
    /** The block the cursor aims at while held, on the mound above the ground. */
    private static final BlockPos AIMED = GROUND.above();
    /** Obsidian in the cursor's 3x3, above the plane but outside the flatten tag. */
    private static final BlockPos OUTSIDE_TAG = AIMED.above().north();
    private static final int HELD_GOO = 2;
    private static final double FACE_CENTER = 0.5;
    private static final double ITEM_SEARCH_RADIUS = 3;
    private static final int ASSERT_TICK = 3;
    private static final Identifier ROCK_FLATTEN = Identifier.parse("goo:rock_flatten");
    private static final String ABILITY_REQUIRED = "Ability registry must hold rock_flatten";

    private FlattenChannelTests() {
    }

    /**
     * A mock player begins the hold with the cursor on a ground block, then
     * holds flatten aimed at the west face of the dirt mound standing on it:
     * the six dirt blocks of the cursor's upright 3x3 above the ground's top
     * break and drop dirt, the obsidian among them stays, and the ground row
     * of the 3x3 stays.
     *
     * @param helper the gametest helper
     */
    public static void flattenBreaksAboveThePlane(GameTestHelper helper) {
        AbilityDefinition flatten = AbilityRegistry.of(helper.getLevel()).getAbility(ROCK_FLATTEN);
        helper.assertTrue(flatten != null, ABILITY_REQUIRED);
        for (int z = -1; z <= 1; z++) {
            for (int y = 0; y <= 2; y++) {
                helper.setBlock(GROUND.offset(0, y, z), Blocks.DIRT);
            }
        }
        helper.setBlock(OUTSIDE_TAG, Blocks.OBSIDIAN);
        ServerPlayer player = flattener(helper);
        KnownRecipes.teachRequires(player, flatten);
        double plane = ChannelAim.planeAbove(helper.absolutePos(GROUND).getY());
        GooStreamPayload tick = new GooStreamPayload(GooTypes.id(GooTypes.ROCK), ROCK_FLATTEN.toString(),
                player.getEyePosition(), westFace(helper, AIMED), plane);
        helper.runAfterDelay(1, () -> GooStreamHandler.streamTick(player, tick));
        helper.runAfterDelay(ASSERT_TICK, () -> {
            helper.getLevel().getServer().getPlayerList().remove(player);
            for (int z = -1; z <= 1; z++) {
                helper.assertBlockPresent(Blocks.DIRT, GROUND.offset(0, 0, z));
                for (int y = 1; y <= 2; y++) {
                    BlockPos above = GROUND.offset(0, y, z);
                    if (!above.equals(OUTSIDE_TAG)) {
                        helper.assertBlockPresent(Blocks.AIR, above);
                    }
                }
            }
            helper.assertBlockPresent(Blocks.OBSIDIAN, OUTSIDE_TAG);
            helper.assertItemEntityPresent(Items.DIRT, AIMED, ITEM_SEARCH_RADIUS);
            helper.succeed();
        });
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
