package com.mercuriusxeno.goo.block.plexer;

import com.mercuriusxeno.goo.data.KnownItems;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The plexer refuses a target the player has not learned and takes one the
 * player knows (decision plexer-refuses-an-unlearned-item).
 */
class CutawayInteractionHelperTest {

    private static final Identifier STONE = Identifier.withDefaultNamespace("stone");

    @Test
    void unlearnedItemIsRefused() {
        assertTrue(CutawayInteractionHelper.refusesTarget(STONE, KnownItems.NONE));
    }

    @Test
    void learnedItemIsTaken() {
        assertFalse(CutawayInteractionHelper.refusesTarget(STONE, KnownItems.NONE.with(STONE)));
    }
}
