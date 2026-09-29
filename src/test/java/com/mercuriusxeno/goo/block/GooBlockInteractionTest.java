package com.mercuriusxeno.goo.block;

import com.mercuriusxeno.goo.item.GooInteractionType;
import net.minecraft.world.InteractionResult;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests the dispatcher's row gate: a click reaches a machine's dispatcher only for a row the
 * machine answers, a tuner or gasket the machine does not answer passes to the item's own use,
 * and every other click falls to the empty-hand path
 * (decision every-machine-clicks-through-the-dispatcher). The gate takes no world state, so
 * no Minecraft world class is touched.
 */
class GooBlockInteractionTest {

    private static final Set<GooInteractionType> BLOB_ONLY = Set.of(GooInteractionType.BLOB_INSERT);

    @Test
    void rowTheMachineDoesNotAnswerFallsToTheEmptyHand() {
        assertEquals(InteractionResult.TRY_WITH_EMPTY_HAND,
                GooBlockInteraction.rowGate(GooInteractionType.CANISTER_INSERT, BLOB_ONLY));
    }

    @Test
    void unclassifiedItemFallsToTheEmptyHand() {
        assertEquals(InteractionResult.TRY_WITH_EMPTY_HAND, GooBlockInteraction.rowGate(null, BLOB_ONLY));
    }

    @Test
    void gasketTheMachineDoesNotAnswerPassesToTheItem() {
        assertEquals(InteractionResult.PASS,
                GooBlockInteraction.rowGate(GooInteractionType.GASKET_INSTALL, BLOB_ONLY));
    }

    @Test
    void gasketTheMachineAnswersReachesItsDispatcher() {
        assertNull(GooBlockInteraction.rowGate(GooInteractionType.GASKET_INSTALL,
                Set.of(GooInteractionType.GASKET_INSTALL)));
    }
}
