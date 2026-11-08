package com.mercuriusxeno.goo.item;

import com.mercuriusxeno.goo.item.GhostItemTargeting.GhostUse;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A ghost used on a plexer's cutaway targets the item it stands for: a plain use discards the ghost,
 * a sneak use keeps it, and a refused target keeps it; any other stack targets itself and stays in hand
 * (decision ghost-item-targets-the-plexer-sneak-keeps-throw-deletes).
 */
class GhostItemTargetingTest {

    private static final Identifier GHOST = Identifier.fromNamespaceAndPath("goo", "ghost_item");
    private static final Identifier STONE = Identifier.withDefaultNamespace("stone");
    private static final Identifier DIRT = Identifier.withDefaultNamespace("dirt");

    @Nested
    class HeldGhost {
        @Test
        void plainUseTargetsTheRepresentedItemAndEmptiesTheHand() {
            assertEquals(new GhostUse(STONE, false, true),
                    GhostItemTargeting.resolve(GHOST, STONE, false, id -> false));
        }

        @Test
        void sneakUseTargetsTheRepresentedItemAndKeepsTheGhost() {
            assertEquals(new GhostUse(STONE, false, false),
                    GhostItemTargeting.resolve(GHOST, STONE, true, id -> false));
        }

        @Test
        void refusedTargetFizzlesAndKeepsTheGhost() {
            assertEquals(new GhostUse(STONE, true, false),
                    GhostItemTargeting.resolve(GHOST, STONE, false, STONE::equals));
        }
    }

    @Nested
    class HeldItem {
        @Test
        void plainItemTargetsItselfAndStaysInHand() {
            assertEquals(new GhostUse(DIRT, false, false),
                    GhostItemTargeting.resolve(DIRT, null, false, STONE::equals));
        }

        @Test
        void refusedItemFizzlesAndStaysInHand() {
            assertEquals(new GhostUse(STONE, true, false),
                    GhostItemTargeting.resolve(STONE, null, false, STONE::equals));
        }
    }
}
