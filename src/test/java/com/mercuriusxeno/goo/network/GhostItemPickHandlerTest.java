package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.data.KnownItems;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A pick places a ghost only for an item the player knows, into an empty main hand
 * (decision menu-places-a-ghost-item-in-the-empty-hand).
 */
class GhostItemPickHandlerTest {

    private static final Identifier STONE = Identifier.withDefaultNamespace("stone");
    private static final KnownItems KNOWS_STONE = KnownItems.NONE.with(STONE);

    @Test
    void knownItemIntoAnEmptyHandPlacesTheGhost() {
        assertTrue(GhostItemPickHandler.placesGhost(KNOWS_STONE, true, STONE));
    }

    @Test
    void unknownItemPlacesNothing() {
        assertFalse(GhostItemPickHandler.placesGhost(KnownItems.NONE, true, STONE));
    }

    @Test
    void fullHandPlacesNothing() {
        assertFalse(GhostItemPickHandler.placesGhost(KNOWS_STONE, false, STONE));
    }
}
