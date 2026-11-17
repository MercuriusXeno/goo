package com.mercuriusxeno.goo.ability.crystal;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Each formed knife's hover slot is a pure function of its index and the
 * count formed: the same inputs give the same slot, one count's slots mirror
 * left to right about the view's center line, and no two of them coincide
 * (decision knives-emerge-from-the-blob-and-hover-in-a-fan).
 */
class KnifeFanTest {

    private static final double TOLERANCE = 1e-9;
    /** The least distance two knives of one count stand apart, in blocks. */
    private static final double APART = 0.05;

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12})
    void theSameIndexAndCountGiveTheSameSlot(int count) {
        for (int index = 0; index < count; index++) {
            assertEquals(KnifeFan.slotOf(index, count), KnifeFan.slotOf(index, count));
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12})
    void aCountsSlotsMirrorLeftToRightAboutTheViewsCenterLine(int count) {
        for (int index = 0; index < count; index++) {
            KnifeFan.Slot slot = KnifeFan.slotOf(index, count);
            KnifeFan.Slot mirror = KnifeFan.slotOf(count - 1 - index, count);
            assertEquals(-slot.right(), mirror.right(), TOLERANCE, "knife " + index + " of " + count);
            assertEquals(slot.up(), mirror.up(), TOLERANCE, "knife " + index + " of " + count);
            assertEquals(slot.ahead(), mirror.ahead(), TOLERANCE, "knife " + index + " of " + count);
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12})
    void noTwoSlotsOfOneCountCoincide(int count) {
        List<KnifeFan.Slot> slots = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            KnifeFan.Slot slot = KnifeFan.slotOf(index, count);
            for (KnifeFan.Slot other : slots) {
                assertTrue(distance(slot, other) > APART, "knife " + index + " of " + count + " sits on another");
            }
            slots.add(slot);
        }
    }

    private static double distance(KnifeFan.Slot a, KnifeFan.Slot b) {
        return Math.sqrt(Math.pow(a.right() - b.right(), 2) + Math.pow(a.up() - b.up(), 2)
                + Math.pow(a.ahead() - b.ahead(), 2));
    }
}
