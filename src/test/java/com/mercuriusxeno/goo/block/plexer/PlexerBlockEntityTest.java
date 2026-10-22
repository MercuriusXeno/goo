package com.mercuriusxeno.goo.block.plexer;

import com.mercuriusxeno.goo.data.GooFraction;
import com.mercuriusxeno.goo.data.GooRemainders;
import com.mercuriusxeno.goo.data.GooValue;
import com.mercuriusxeno.goo.data.RemainderLedger;
import com.mercuriusxeno.goo.type.GooTypes;
import org.junit.jupiter.api.Test;
import java.util.List;
import static com.mercuriusxeno.goo.data.TestRecipeBuilder.fraction;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The plexer fabricates at the exact cost through its ledger, read through a fake goo supply
 * (decision melt-and-fabricate-move-the-exact-value-through-a-remainder-buffer).
 */
class PlexerBlockEntityTest {

    private static final GooValue CARPET = fraction(GooTypes.LEAF, 64, 3);

    @Test
    void threeTargetsWorthSixtyFourThirdsCostSixtyFourInAll() {
        MapGooSupply supply = MapGooSupply.holding(GooTypes.LEAF, 100);
        RemainderLedger ledger = new RemainderLedger();

        assertTrue(PlexerBlockEntity.fabricateFrom(CARPET, supply, ledger));
        assertEquals(GooFraction.of(2, 3), ledger.remainders().of(GooTypes.LEAF));
        assertTrue(PlexerBlockEntity.fabricateFrom(CARPET, supply, ledger));
        assertEquals(GooFraction.of(1, 3), ledger.remainders().of(GooTypes.LEAF));
        assertTrue(PlexerBlockEntity.fabricateFrom(CARPET, supply, ledger));

        assertEquals(List.of(22, 21, 21), supply.consumed());
        assertTrue(ledger.remainders().isEmpty());
    }

    @Test
    void theRemainderPaysFirstSoASupplyOfTwentyOneCovers() {
        MapGooSupply supply = MapGooSupply.holding(GooTypes.LEAF, 21);
        RemainderLedger ledger = new RemainderLedger(GooRemainders.EMPTY.with(GooTypes.LEAF, GooFraction.of(1, 3)));

        assertTrue(PlexerBlockEntity.fabricateFrom(CARPET, supply, ledger));

        assertEquals(List.of(21), supply.consumed());
        assertTrue(ledger.remainders().isEmpty());
    }
}
