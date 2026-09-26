package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.registry.GooItems;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;

/**
 * Gametest for the glove's right click entering no visible using state
 * (decision use-animation-only-when-goo-throws).
 */
public final class GloveUseTests {

    private static final String REMOVAL = "removal";
    private static final String USING = "A right click with the glove should leave the player out of the using state";
    private static final String REEQUIPS = "Minecraft.startUseItem drops and re-raises the held item on a Success, so the glove should answer a non-Success, got ";

    private GloveUseTests() {
    }

    /**
     * A right click with the glove leaves the player out of the using state
     * and answers no Success, the result vanilla re-equips the hand on.
     *
     * @param helper the gametest helper
     */
    @SuppressWarnings(REMOVAL) // vanilla marks the mock server player helper for removal and names no replacement
    public static void rightClickEntersNoUsingState(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ItemStack glove = new ItemStack(GooItems.GOO_GLOVE.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, glove);

        InteractionResult result = glove.getItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);

        boolean using = player.isUsingItem();
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.assertFalse(using, USING);
        helper.assertFalse(result instanceof InteractionResult.Success, REEQUIPS + result);
        helper.succeed();
    }
}
