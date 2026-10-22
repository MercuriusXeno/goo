package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.data.GooFraction;
import com.mercuriusxeno.goo.data.RemainderLedger;
import com.mercuriusxeno.goo.item.GooContents;
import com.mercuriusxeno.goo.type.GooTypes;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static com.mercuriusxeno.goo.data.TestRecipeBuilder.fraction;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unmake's payout credits a drunk block's exact value through the player's ledger,
 * free of the level (decision melt-and-fabricate-move-the-exact-value-through-a-remainder-buffer).
 */
class DrinksTest {

    @Test
    void aBlockWorthSixtyFourThirdsLandsTwentyOneAndKeepsAThird() {
        RemainderLedger ledger = new RemainderLedger();

        GooContents landed = Drinks.payoutThrough(fraction(GooTypes.LEAF, 64, 3), ledger);

        assertEquals(new GooContents(Map.of(GooTypes.LEAF, 21)), landed);
        assertEquals(GooFraction.of(1, 3), ledger.remainders().of(GooTypes.LEAF));
    }

    @Test
    void threeBlocksWorthSixtyFourThirdsLandSixtyFourInAll() {
        RemainderLedger ledger = new RemainderLedger();
        int landed = 0;

        for (int block = 0; block < 3; block++) {
            landed += Drinks.payoutThrough(fraction(GooTypes.LEAF, 64, 3), ledger).getVolume(GooTypes.LEAF);
        }

        assertEquals(64, landed);
    }
}
