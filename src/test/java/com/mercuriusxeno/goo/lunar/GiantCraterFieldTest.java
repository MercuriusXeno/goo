package com.mercuriusxeno.goo.lunar;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A giant lunar crater sinks a broad floor under the ground, raises a central peak over that floor, steps its inner
 * wall up in terraces to a raised rim, and leaves the ground past its rim's fall untouched; and it reads its
 * biome at its centre, rare in maria, old and worn in the highlands, fresh in the shadowed basins.
 * decision lunar-surface-gains-giant-craters-mountains-rilles-and-boulder-fields
 * decision lunar-surface-has-maria-highlands-shadowed-basins-and-eldritch-ground
 */
class GiantCraterFieldTest {

    private static final double TOLERANCE = 1e-9;
    private static final double RADIUS = 160.0;
    private static final double DEPTH = RADIUS * GiantCraterField.DEPTH_PER_RADIUS;

    @Nested
    class Profile {

        @Test
        void theFloorSinksBelowTheGround() {
            double floorReach = (GiantCraterField.PEAK_REACH + GiantCraterField.FLOOR_REACH) / 2.0;
            assertEquals(-DEPTH, GiantCraterField.profile(floorReach, RADIUS), TOLERANCE);
        }

        @Test
        void theCentreRisesToAPeakOverTheFloor() {
            assertTrue(GiantCraterField.profile(0.0, RADIUS) > -DEPTH,
                    "the centre should stand over the floor at " + (-DEPTH));
        }

        @Test
        void eachTerraceIsFlatAcrossItsTread() {
            for (int terrace = 0; terrace < GiantCraterField.TERRACES; terrace++) {
                double treadStart = terraceReach(terrace, 0.0);
                double treadEnd = terraceReach(terrace, GiantCraterField.TREAD * 0.9);
                assertEquals(GiantCraterField.profile(treadStart, RADIUS), GiantCraterField.profile(treadEnd, RADIUS),
                        TOLERANCE, "terrace " + terrace);
            }
        }

        @Test
        void eachTerraceStandsOverTheOneInsideIt() {
            for (int terrace = 1; terrace < GiantCraterField.TERRACES; terrace++) {
                assertTrue(GiantCraterField.profile(terraceReach(terrace, 0.0), RADIUS)
                        > GiantCraterField.profile(terraceReach(terrace - 1, 0.0), RADIUS), "terrace " + terrace);
            }
        }

        @Test
        void theRimStandsAboveTheGround() {
            assertEquals(RADIUS * GiantCraterField.RIM_PER_RADIUS, GiantCraterField.profile(1.0, RADIUS), TOLERANCE);
        }

        @Test
        void pastItsReachTheCraterLeavesTheGround() {
            assertEquals(0.0, GiantCraterField.profile(1.0 + GiantCraterField.RIM_REACH, RADIUS), TOLERANCE);
        }

        /** The reach a share of the way across a terrace, from its tread's inner edge. */
        private static double terraceReach(int terrace, double across) {
            double wall = 1.0 - GiantCraterField.FLOOR_REACH;
            return GiantCraterField.FLOOR_REACH + wall * (terrace + across) / GiantCraterField.TERRACES;
        }
    }

    @Nested
    class ByItsBiome {

        private static final double IN_MARIA = 0.3;
        private static final double OUTSIDE_MARIA = -0.3;
        private static final double IN_HIGHLANDS = -0.4;
        private static final double IN_BASINS = 0.6;
        /** A presence roll a crater centred outside maria keeps and one centred in maria loses. */
        private static final double MIDDLING_PRESENCE = GiantCraterField.CRATER_CHANCE * 0.5;

        @Test
        void aCraterCentredInMariaIsRarerThanOneOutside() {
            assertTrue(GiantCraterField.holdsCrater(MIDDLING_PRESENCE, OUTSIDE_MARIA));
            assertFalse(GiantCraterField.holdsCrater(MIDDLING_PRESENCE, IN_MARIA));
        }

        @Test
        void aCraterInTheHighlandsIsWornWhateverItsRoll() {
            assertFalse(GiantCraterField.isFresh(0.0, OUTSIDE_MARIA, IN_HIGHLANDS));
        }

        @Test
        void aCraterInTheShadowedBasinsIsFreshWhateverItsRoll() {
            assertTrue(GiantCraterField.isFresh(0.999, OUTSIDE_MARIA, IN_BASINS));
        }

        @Test
        void aCraterInMariaIsFreshAsItsRollFalls() {
            assertTrue(GiantCraterField.isFresh(0.0, IN_MARIA, IN_HIGHLANDS));
            assertFalse(GiantCraterField.isFresh(0.999, IN_MARIA, IN_BASINS));
        }
    }
}
