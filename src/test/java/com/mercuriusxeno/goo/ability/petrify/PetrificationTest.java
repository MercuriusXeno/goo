package com.mercuriusxeno.goo.ability.petrify;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A petrify gauge fills toward full, becomes a statue the fill it reaches
 * full and stays one (decision petrify-stone-encasement-and-calcify-map).
 */
class PetrificationTest {

    private static final float DELTA = 1e-6f;

    @Test
    void fillsShortOfFullStayFlesh() {
        Petrification filled = Petrification.NONE.fill(40f).fill(40f);
        assertEquals(80f, filled.gauge(), DELTA);
        assertFalse(filled.statue());
        assertTrue(filled.started());
    }

    @Test
    void reachingFullMakesAStatueCappedAtFull() {
        Petrification statue = Petrification.NONE.fill(90f).fill(30f);
        assertTrue(statue.statue());
        assertEquals(Petrification.FULL, statue.gauge(), DELTA);
    }

    @Test
    void aStatueStaysOne() {
        Petrification statue = Petrification.NONE.fill(Petrification.FULL);
        assertSame(statue, statue.fill(5f));
    }

    @Test
    void anUntouchedMobHoldsNothingToSave() {
        assertFalse(Petrification.NONE.started());
    }
}
