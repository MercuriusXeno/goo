package com.mercuriusxeno.goo.client.machine;

import com.mercuriusxeno.goo.GooTypes;
import com.mercuriusxeno.goo.client.machine.VatStackAggregator.VatStackData;
import com.mercuriusxeno.goo.item.GooContents;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests whether aggregated vat stack data has anything for the HUD panel to show; a stack
 * holding water alone shows (decision diagnose-then-fix-vat-hud-water-row).
 */
class VatStackAggregatorTest {

    /** A stack holding nothing, unlabeled and unupgraded, shows nothing. */
    @Test
    void bareStackHasNothingToShow() {
        assertTrue(stack(GooContents.EMPTY, 0, 0, null).hasNothingToShow());
    }

    /** A stack holding water alone shows its panel. */
    @Test
    void waterOnlyStackHasSomethingToShow() {
        assertFalse(stack(GooContents.EMPTY, 1000, 0, null).hasNothingToShow());
    }

    /** A stack holding goo shows its panel. */
    @Test
    void gooStackHasSomethingToShow() {
        assertFalse(stack(new GooContents(Map.of(GooTypes.ROCK, 1)), 0, 0, null).hasNothingToShow());
    }

    /** An upgraded empty stack shows its panel. */
    @Test
    void upgradedStackHasSomethingToShow() {
        assertFalse(stack(GooContents.EMPTY, 0, 1, null).hasNothingToShow());
    }

    /** A labeled empty stack shows its panel. */
    @Test
    void labeledStackHasSomethingToShow() {
        assertFalse(stack(GooContents.EMPTY, 0, 0, "Tank").hasNothingToShow());
    }

    /**
     * Builds single vat stack data with no gaskets.
     *
     * @param contents    the stack's summed goo contents
     * @param water       the stack's summed water
     * @param compression the targeted vat's compression level
     * @param label       the targeted vat's label, or null
     * @return the stack data
     */
    private static VatStackData stack(GooContents contents, long water, int compression, String label) {
        return new VatStackData(contents, water, compression, false, false, label, null, null, 1);
    }
}
