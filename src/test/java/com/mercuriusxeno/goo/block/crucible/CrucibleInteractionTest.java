package com.mercuriusxeno.goo.block.crucible;

import com.mercuriusxeno.goo.item.GooInteractionType;
import org.junit.jupiter.api.Test;
import java.util.EnumSet;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests which clicks the crucible's dispatcher answers: a canister is any other item and falls
 * through to the empty-hand drain (decision canister-click-is-any-other-click-on-crucible-and-vat).
 */
class CrucibleInteractionTest {

    @Test
    void crucibleAnswersOnlyTheSparkAndTheBlob() {
        assertEquals(EnumSet.of(GooInteractionType.SPARK, GooInteractionType.BLOB_INSERT),
                EnumSet.copyOf(CrucibleBlock.CLICK_ROWS));
    }
}
