package com.mercuriusxeno.goo.item;

import com.mercuriusxeno.goo.data.GooFraction;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A pour settles the stack's exact value against the whole mB the sink accepted: a store
 * taking the whole mB entire is credited the part, and one taking less leaves the part on
 * the stack beside the whole mB it left.
 * decision goo-item-carries-its-exact-value-as-a-mixed-remainder
 */
class GooInsertTest {

    private static final MixedVolume FOUR_AND_TWO_THIRDS = new MixedVolume(4, GooFraction.of(2, 3));

    @Test
    void aPourTakingSomeWholeMbLeavesThePartOnTheStack() {
        assertEquals(new GooInsert.PourSettlement(new MixedVolume(1, GooFraction.of(2, 3)), GooFraction.ZERO),
                GooInsert.settle(FOUR_AND_TWO_THIRDS, 3));
    }

    @Test
    void aPourTakingEveryWholeMbCreditsThePart() {
        assertEquals(new GooInsert.PourSettlement(MixedVolume.ZERO, GooFraction.of(2, 3)),
                GooInsert.settle(FOUR_AND_TWO_THIRDS, 4));
    }
}
