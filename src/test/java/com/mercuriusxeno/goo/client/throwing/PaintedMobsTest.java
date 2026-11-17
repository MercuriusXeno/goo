package com.mercuriusxeno.goo.client.throwing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.OptionalInt;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A Knives hold's painted list holds each hovered mob once in the order
 * first hovered, keeps a mob after the outline leaves it, drops a mob no
 * longer standing, and empties on a throw and on a dismiss
 * (decision knives-paint-mobs-by-hovering-while-forming).
 */
class PaintedMobsTest {

    private static final int MOB_A = 11;
    private static final int MOB_B = 22;

    private final PaintedMobs paint = new PaintedMobs();

    @BeforeEach
    void hoverAThenBThenAThenNothing() {
        paint.hovered(OptionalInt.of(MOB_A));
        paint.hovered(OptionalInt.of(MOB_B));
        paint.hovered(OptionalInt.of(MOB_A));
        paint.hovered(OptionalInt.empty());
    }

    @Test
    void eachHoveredMobIsPaintedOnceInTheOrderFirstHoveredAndStaysAfterTheOutlineLeaves() {
        assertEquals(List.of(MOB_A, MOB_B), paint.painted());
    }

    @Test
    void aThrowEmptiesTheList() {
        paint.thrown();

        assertEquals(List.of(), paint.painted());
    }

    @Test
    void aDismissEmptiesTheList() {
        paint.dismissed();

        assertEquals(List.of(), paint.painted());
    }

    @Test
    void aMobNoLongerStandingIsDropped() {
        paint.dropGone(id -> id != MOB_A);

        assertEquals(List.of(MOB_B), paint.painted());
    }
}
