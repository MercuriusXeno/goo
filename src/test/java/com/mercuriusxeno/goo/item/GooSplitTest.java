package com.mercuriusxeno.goo.item;

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
}
