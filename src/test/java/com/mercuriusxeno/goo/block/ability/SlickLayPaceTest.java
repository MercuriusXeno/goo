package com.mercuriusxeno.goo.block.ability;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Slick lays faster by a channeler's horizontal speed over their unadjusted
 * movement speed while they stand on slick, and never slower than the JSON's
 * ticks.
 * slick-resolves-faster-by-speed-over-unadjusted-speed
 */
class SlickLayPaceTest {

    private static final double EXACT = 1e-9;
    /** A default player's movement speed attribute, walking. */
    private static final double WALKING_ATTRIBUTE = 0.1;
    /** A default player's movement speed attribute, sprinting: the walk's plus three tenths. */
    private static final double SPRINTING_ATTRIBUTE = 0.13;
    private static final double UNADJUSTED = SlickLayPace.unadjustedSpeed(WALKING_ATTRIBUTE);

    @Nested
    class OnSlickTheSpeedSetsThePace {

        @Test
        void twiceTheUnadjustedSpeedLaysTwiceAsFast() {
            assertEquals(2, SlickLayPace.factor(2 * UNADJUSTED, UNADJUSTED, true), EXACT);
        }
    }

    @Nested
    class TheFactorNeverFallsBelowOne {

        @Test
        void aChannelerOffSlickLaysAtTheJsonsTicksHoweverFast() {
            assertEquals(1, SlickLayPace.factor(3 * UNADJUSTED, UNADJUSTED, false), EXACT);
        }

        @Test
        void aChannelerAtRestOnSlickLaysAtTheJsonsTicks() {
            assertEquals(1, SlickLayPace.factor(0, UNADJUSTED, true), EXACT);
        }

        @Test
        void aChannelerSlowerThanTheirUnadjustedSpeedLaysAtTheJsonsTicks() {
            assertEquals(1, SlickLayPace.factor(UNADJUSTED / 2, UNADJUSTED, true), EXACT);
        }
    }

    @Nested
    class TheUnadjustedSpeedIsTheGroundWalk {

        @Test
        void aWalkingPlayerWalksTheGroundAtVanillasWalkSpeed() {
            assertEquals(0.2159, SlickLayPace.unadjustedSpeed(WALKING_ATTRIBUTE), 1e-4);
        }

        @Test
        void aSprintingPlayerWalksTheGroundAtVanillasSprintSpeed() {
            assertEquals(0.2806, SlickLayPace.unadjustedSpeed(SPRINTING_ATTRIBUTE), 1e-4);
        }
    }
}
