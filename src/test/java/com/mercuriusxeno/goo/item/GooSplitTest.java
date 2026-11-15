package com.mercuriusxeno.goo.item;

import com.mercuriusxeno.goo.data.GooFraction;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests that the empty-cursor right-click halves a goo at every volume
 * (decision right-click-halves-the-stack).
 */
class GooSplitTest {

    /**
     * The cursor takes the floored half and the slot keeps the larger half;
     * 1 mB goes to the cursor whole and the slot empties.
     *
     * @param volume       the goo's volume
     * @param slotVolume   the volume the slot keeps
     * @param cursorVolume the volume the cursor takes
     */
    @ParameterizedTest
    @CsvSource({"1000, 500, 500", "700, 350, 350", "5, 3, 2", "1, 0, 1"})
    void rightClickHalvesEveryVolume(int volume, int slotVolume, int cursorVolume) {
        GooSplit.Halves halves = GooSplit.halve(volume);
        assertEquals(slotVolume, halves.slotVolume());
        assertEquals(cursorVolume, halves.cursorVolume());
    }

    /**
     * A halve keeps the sum: the cursor takes the floored half of the whole mB, the slot
     * keeps the larger half and the part under 1 mB, and a part alone goes to the cursor.
     * decision goo-item-carries-its-exact-value-as-a-mixed-remainder
     *
     * @param whole       the goo's whole mB
     * @param numerator   its part's numerator
     * @param denominator its part's denominator
     * @param slotWhole   the whole mB the slot keeps
     * @param slotNum     the slot's part numerator
     * @param slotDen     the slot's part denominator
     * @param cursorWhole the whole mB the cursor takes
     * @param cursorNum   the cursor's part numerator
     * @param cursorDen   the cursor's part denominator
     */
    @ParameterizedTest
    @CsvSource({"5, 1, 3, 3, 1, 3, 2, 0, 1", "1, 1, 2, 0, 1, 2, 1, 0, 1", "0, 1, 3, 0, 0, 1, 0, 1, 3"})
    void rightClickHalveKeepsThePartWithTheSlot(int whole, int numerator, int denominator,
            int slotWhole, int slotNum, int slotDen, int cursorWhole, int cursorNum, int cursorDen) {
        MixedVolume volume = new MixedVolume(whole, GooFraction.of(numerator, denominator));
        GooSplit.MixedHalves halves = GooSplit.halve(volume);
        assertEquals(new MixedVolume(slotWhole, GooFraction.of(slotNum, slotDen)), halves.slot());
        assertEquals(new MixedVolume(cursorWhole, GooFraction.of(cursorNum, cursorDen)), halves.cursor());
        assertEquals(volume.exact(), halves.slot().exact().plus(halves.cursor().exact()));
    }
}
