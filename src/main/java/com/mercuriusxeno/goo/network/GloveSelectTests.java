package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.GooTypes;
import com.mercuriusxeno.goo.ability.GloveSelection;
import com.mercuriusxeno.goo.item.GooGloveItem;
import com.mercuriusxeno.goo.registry.GooItems;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;

/**
 * Gametests for {@link GloveSelectHandler}: a selection naming a type and
 * no ability leaves the glove as it was, and a type with one of its
 * abilities is what the glove then holds (decision
 * no-throw-without-ability). They sit in the handler's package because the
 * handler's apply step is package-private.
 */
public final class GloveSelectTests {

    private static final String BLAZE_ID = GooTypes.id(GooTypes.BLAZE);
    private static final String BLAZE_TUNNEL = "goo:blaze_tunnel";
    private static final String NO_ABILITY = "";
    private static final String TYPE_ONLY_TOOK = "A type with no ability changed the glove's selection";
    private static final String ABILITY_NOT_HELD = "The glove does not hold the type and ability sent";

    private GloveSelectTests() {
    }

    /**
     * Sends a type with no ability to an empty glove, which stays empty,
     * then a type with its ability, which the glove holds.
     *
     * @param helper the gametest helper
     */
    public static void typeOnlySelectionRefused(GameTestHelper helper) {
        ItemStack glove = new ItemStack(GooItems.GOO_GLOVE.get());
        GloveSelectHandler.resolveAndApply(glove, new GloveSelectPayload(BLAZE_ID, NO_ABILITY));
        helper.assertTrue(GooGloveItem.getSelection(glove) == null, TYPE_ONLY_TOOK);

        GloveSelectHandler.resolveAndApply(glove, new GloveSelectPayload(BLAZE_ID, BLAZE_TUNNEL));
        GloveSelection held = GooGloveItem.getSelection(glove);
        helper.assertTrue(held != null && BLAZE_ID.equals(held.gooTypeId())
                && BLAZE_TUNNEL.equals(held.abilityId()), ABILITY_NOT_HELD);

        GloveSelectHandler.resolveAndApply(glove, new GloveSelectPayload(BLAZE_ID, NO_ABILITY));
        helper.assertTrue(held.equals(GooGloveItem.getSelection(glove)), TYPE_ONLY_TOOK);
        helper.succeed();
    }
}
