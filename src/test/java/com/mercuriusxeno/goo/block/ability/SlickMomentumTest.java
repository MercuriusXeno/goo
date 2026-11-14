package com.mercuriusxeno.goo.block.ability;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A thing carries slick's momentum for half a second after it last stood on
 * slick, and no longer (operator ruling 2026-11-14: conserve the momentum
 * over raised slick cells and short bare gaps).
 */
class SlickMomentumTest {

    private static final long LEFT_SLICK_AT = 100;

    @Test
    void aThingStillOnSlickCarriesItsMomentum() {
        assertTrue(SlickMomentum.carries(LEFT_SLICK_AT, LEFT_SLICK_AT));
    }

    @Test
    void aThingHalfASecondOffSlickStillCarriesIt() {
        assertTrue(SlickMomentum.carries(LEFT_SLICK_AT, LEFT_SLICK_AT + SlickMomentum.CARRY_TICKS));
    }

    @Test
    void aThingLongerOffSlickCarriesNothing() {
        assertFalse(SlickMomentum.carries(LEFT_SLICK_AT, LEFT_SLICK_AT + SlickMomentum.CARRY_TICKS + 1));
    }
}
