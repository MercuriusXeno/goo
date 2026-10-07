package com.mercuriusxeno.goo.client;

import com.mercuriusxeno.goo.data.KnownItems;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The tooltip shows an item's goo value and its hint only once the player
 * has learned the item, while goo a stack carries always shows
 * (decision goo-tooltip-shows-only-known-values), and the hint names the
 * key bound to Goo values.
 */
class GooTooltipHandlerTest {

    private static final Identifier COBBLESTONE = Identifier.withDefaultNamespace("cobblestone");
    private static final Identifier CANISTER = Identifier.fromNamespaceAndPath("goo", "canister");

    @Test
    void unknownItemShowsNoValue() {
        assertFalse(GooTooltipHandler.revealsGooLines(false, COBBLESTONE, KnownItems.NONE));
    }

    @Test
    void knownItemShowsItsValue() {
        assertTrue(GooTooltipHandler.revealsGooLines(false, COBBLESTONE, KnownItems.NONE.with(COBBLESTONE)));
    }

    @Test
    void containerShowsTheGooItCarriesUnlearned() {
        assertTrue(GooTooltipHandler.revealsGooLines(true, CANISTER, KnownItems.NONE));
    }

    /** decision tooltip-key-is-its-own-g-binding */
    @Test
    void hintNamesTheBoundGooValuesKey() {
        assertEquals("Hold [G] for goo values",
                GooTooltipHandler.revealHint(Component.literal("G")).getString());
    }
}
