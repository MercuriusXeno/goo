package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.item.GooStacks;
import com.mercuriusxeno.goo.registry.GooItems;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/**
 * A mock player standing in a test's bay with a goo glove in hand and ten
 * goo of one type, for gametests that throw through the real throw path.
 */
final class MockThrowers {

    private static final String REMOVAL = "removal";
    /** Where the thrower stands in the bay. */
    private static final BlockPos PLAYER_POS = new BlockPos(1, 2, 4);
    /** The goo the thrower holds, in whole goo. */
    private static final int HELD_GOO = 10;

    private MockThrowers() {
    }

    /**
     * @param helper the gametest helper
     * @param type   the goo type the thrower holds
     * @return a mock player in the level holding a glove and ten goo of the type
     */
    @SuppressWarnings(REMOVAL) // vanilla marks the mock server player helper for removal and names no replacement
    static ServerPlayer makeThrower(GameTestHelper helper, ResourceKey<GooTypeDefinition> type) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        BlockPos stand = helper.absolutePos(PLAYER_POS);
        player.setPos(stand.getX(), stand.getY(), stand.getZ());
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(GooItems.GOO_GLOVE.get()));
        player.getInventory().add(GooStacks.createForOutput(type, HELD_GOO * GooStacks.THOUSAND));
        return player;
    }
}
