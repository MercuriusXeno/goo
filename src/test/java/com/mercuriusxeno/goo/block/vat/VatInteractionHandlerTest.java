package com.mercuriusxeno.goo.block.vat;

import com.mercuriusxeno.goo.item.GooInteractionType;
import org.junit.jupiter.api.Test;
import java.util.EnumSet;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests which clicks the vat's dispatcher answers: a canister is any other item and falls
 * through to the empty-hand unpack (decision canister-click-is-any-other-click-on-crucible-and-vat).
 */
class VatInteractionHandlerTest {

    @Test
    void vatAnswersOnlyTheGasketAndTheGoo() {
        assertEquals(EnumSet.of(GooInteractionType.GASKET_INSTALL, GooInteractionType.GOO_INSERT),
                EnumSet.copyOf(VatBlock.CLICK_ROWS));
    }
}
