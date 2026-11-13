package com.mercuriusxeno.goo.block.crucible;

import com.mercuriusxeno.goo.block.plexer.MapGooSupply;
import com.mercuriusxeno.goo.block.plexer.PlexerBlockEntity;
import com.mercuriusxeno.goo.data.GooValue;
import com.mercuriusxeno.goo.data.RemainderLedger;
import com.mercuriusxeno.goo.type.GooTypes;
import org.junit.jupiter.api.Test;
import static com.mercuriusxeno.goo.data.TestRecipeBuilder.fraction;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Melting and fabricating move the exact value through each store's ledger, so no
 * melt and fabricate chain on a fractional value ends with more whole goo than it
 * started with (decision melt-and-fabricate-move-the-exact-value-through-a-remainder-buffer).
 * A melt of 64/3 pays 21 (CrucibleInsertionTest) and a fabricate of it costs 22 whole
 * (PlexerBlockEntityTest), so the 21 a melt pays cannot fabricate the item again.
 */
class MeltFabricateChainTest {

    private static final GooValue CARPET_VALUE = fraction(GooTypes.LEAF, 64, 3);
    /** What one carpet worth 64/3 melts to with an empty ledger. */
    private static final int MELTED = 21;

    @Test
    void theWholeGooAMeltPaysCannotFabricateTheItemAgain() {
        assertEquals(false, PlexerBlockEntity.fabricateFrom(CARPET_VALUE, MapGooSupply.holding(GooTypes.LEAF, MELTED),
                new RemainderLedger()));
    }
}
