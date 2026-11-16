package com.mercuriusxeno.goo.client.ability;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.function.DoubleUnaryOperator;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The gate trip's curves: over a moon fixation the sky dims and the land darkens from none to full, and the moon
 * swells from vanilla's size to cover the whole view; white floods in to stand full at the carry, holds while the
 * arrival mark is awaited, and clears after it lands.
 * decision gate-fixates-your-view-on-the-moon-until-it-takes-you
 */
class GateTripTest {

    private static final int SAMPLES = 200;
    private static final double EXACT = 1e-6;

    /**
     * Whether a curve never falls across the fixation and ends above where it starts.
     *
     * @param curve the curve against progress
     * @return true where it rises
     */
    private static boolean rises(DoubleUnaryOperator curve) {
        double last = curve.applyAsDouble(0.0);
        for (int sample = 1; sample <= SAMPLES; sample++) {
            double now = curve.applyAsDouble((double) sample / SAMPLES);
            if (now < last) {
                return false;
            }
            last = now;
        }
        return last > curve.applyAsDouble(0.0);
    }

    @Nested
    class SkyDim {
        @Test
        void skyStandsUndimmedAtTheStartAndFullyDimAtTheEnd() {
            assertEquals(0.0, GateTrip.skyDim(0F), EXACT);
            assertEquals(1.0, GateTrip.skyDim(1F), EXACT);
        }

        @Test
        void skyDimRisesAcrossTheFixation() {
            assertTrue(rises(progress -> GateTrip.skyDim((float) progress)));
        }
    }

    @Nested
    class LandDark {
        @Test
        void landStandsLitAtTheStartAndFullyDarkAtTheEnd() {
            assertEquals(0.0, GateTrip.landDark(0F), EXACT);
            assertEquals(1.0, GateTrip.landDark(1F), EXACT);
        }

        @Test
        void landDarkRisesAcrossTheFixation() {
            assertTrue(rises(progress -> GateTrip.landDark((float) progress)));
        }
    }

    @Nested
    class White {
        private static final float BEFORE_THE_FLOOD = 0.5F;
        private static final float PAST_THE_FADE = 1000F;

        @Test
        void viewStandsClearBeforeTheFlood() {
            assertEquals(0.0, GateTrip.whiteBeforeCarry(BEFORE_THE_FLOOD), EXACT);
        }

        @Test
        void whiteStandsFullAtTheCarry() {
            assertEquals(1.0, GateTrip.whiteBeforeCarry(1F), EXACT);
        }

        @Test
        void whiteHoldsFullWhileTheArrivalMarkIsAwaited() {
            assertEquals(1.0, GateTrip.whiteAfterCarry(false, PAST_THE_FADE), EXACT);
        }

        @Test
        void whiteHoldsFullAsTheArrivalMarkLands() {
            assertEquals(1.0, GateTrip.whiteAfterCarry(true, 0F), EXACT);
        }

        @Test
        void whiteClearsOnceTheFadeAfterArrivalRuns() {
            assertEquals(0.0, GateTrip.whiteAfterCarry(true, PAST_THE_FADE), EXACT);
        }

        @Test
        void whiteFloodRisesAcrossTheFixation() {
            assertTrue(rises(progress -> GateTrip.whiteBeforeCarry((float) progress)));
        }
    }

    @Nested
    class StarSize {
        @Test
        void blueStarStandsAtItsRestingSizeAtTheStart() {
            assertEquals(GateTrip.RESTING_STAR_HALF_TANGENT, GateTrip.starHalfTangent(0F), EXACT);
        }

        @Test
        void blueStarCoversTheWidestViewAtTheEnd() {
            assertTrue(GateTrip.starHalfTangent(1F) >= GateTrip.WIDEST_VIEW_HALF_TANGENT,
                    "the swollen star's half width " + GateTrip.starHalfTangent(1F)
                            + " should reach the widest view's half diagonal " + GateTrip.WIDEST_VIEW_HALF_TANGENT);
        }

        @Test
        void blueStarSwellsAcrossTheFixation() {
            assertTrue(rises(progress -> GateTrip.starHalfTangent((float) progress)));
        }
    }

    @Nested
    class MoonSize {
        @Test
        void moonStandsAtVanillasSizeAtTheStart() {
            assertEquals(GateTrip.RESTING_MOON_HALF_TANGENT, GateTrip.moonHalfTangent(0F), EXACT);
        }

        @Test
        void moonCoversTheWidestViewAtTheEnd() {
            assertTrue(GateTrip.moonHalfTangent(1F) >= GateTrip.WIDEST_VIEW_HALF_TANGENT,
                    "the swollen moon's half width " + GateTrip.moonHalfTangent(1F)
                            + " should reach the widest view's half diagonal " + GateTrip.WIDEST_VIEW_HALF_TANGENT);
        }

        @Test
        void moonSwellsAcrossTheFixation() {
            assertTrue(rises(progress -> GateTrip.moonHalfTangent((float) progress)));
        }
    }
}
