package com.mercuriusxeno.goo.ability.nether;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The ceiling seam: an inverted body is grounded by a move up that the ceiling stops, and an upright body by a move
 * down that the floor stops.
 * invert-flips-your-gravity-and-lets-you-walk-on-ceilings
 */
class CeilingGroundTest {

    private static final double UP = 0.08;
    private static final double DOWN = -0.08;

    @Nested
    class Inverted {

        @Test
        void groundedAgainstTheCeiling() {
            assertEquals(CeilingGround.Contact.GROUNDED, CeilingGround.contact(true, UP, true));
        }

        @Test
        void fallingWhenTheFloorStopsIt() {
            assertEquals(CeilingGround.Contact.FALLING, CeilingGround.contact(true, DOWN, true));
        }

        @Test
        void fallingInTheAir() {
            assertEquals(CeilingGround.Contact.FALLING, CeilingGround.contact(false, UP, true));
        }
    }

    @Nested
    class Upright {

        @Test
        void groundedOnTheFloor() {
            assertEquals(CeilingGround.Contact.GROUNDED, CeilingGround.contact(true, DOWN, false));
        }

        @Test
        void fallingWhenItHitsItsHead() {
            assertEquals(CeilingGround.Contact.FALLING, CeilingGround.contact(true, UP, false));
        }
    }
}
