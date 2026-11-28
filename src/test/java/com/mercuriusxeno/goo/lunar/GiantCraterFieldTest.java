package com.mercuriusxeno.goo.lunar;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A giant lunar crater sinks a broad floor under the ground, raises a central peak over that floor, steps its inner
 * wall up in terraces to a raised rim, and leaves the ground past its rim's fall untouched; it reads its biome at its
 * centre, old and worn in the highlands, fresh in the shadowed basins; and giant craters stand far apart.
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

    /**
     * Giant craters stand far apart: enumerated across a wide area for a fixed seed, every crater's nearest neighbour
     * lies about 1,000 to 2,000 blocks away.
     * decision craters-are-rare-giant-landmarks-only
     */
    @Nested
    class Spacing {

        /** The sampled area runs this many blocks on a side. */
        private static final int AREA = 16_384;
        private static final double NEAREST_LEAST = 1000.0;
        private static final double NEAREST_MOST = 2000.0;

        @Test
        void neighbouringGiantCratersStandAThousandToTwoThousandBlocksApart() {
            GiantCraterField giants = new GiantCraterField(LunarTestNoises.crater(), LunarTestNoises.maria(),
                    LunarTestNoises.basins());
            int lastCell = Math.floorDiv(AREA, GiantCraterField.CELL) + 1;
            List<GiantCraterField.Crater> craters = new ArrayList<>();
            for (int cellX = -1; cellX <= lastCell; cellX++) {
                for (int cellZ = -1; cellZ <= lastCell; cellZ++) {
                    craters.add(giants.craterInCell(cellX, cellZ));
                }
            }
            int measured = 0;
            for (GiantCraterField.Crater crater : craters) {
                if (crater.centerX() < 0.0 || crater.centerX() >= AREA || crater.centerZ() < 0.0
                        || crater.centerZ() >= AREA) {
                    continue;
                }
                double nearest = craters.stream().filter(other -> other != crater)
                        .mapToDouble(other -> Math.hypot(other.centerX() - crater.centerX(),
                                other.centerZ() - crater.centerZ())).min().orElse(Double.MAX_VALUE);
                measured++;
                assertTrue(nearest >= NEAREST_LEAST && nearest <= NEAREST_MOST, "the giant crater at "
                        + crater.centerX() + ", " + crater.centerZ() + " stands " + nearest + " blocks from its nearest");
            }
            assertTrue(measured > 1, "the area holds " + measured + " giant craters");
        }
    }

    @Nested
    class ByItsBiome {

        private static final double IN_MARIA = 0.3;
        private static final double OUTSIDE_MARIA = -0.3;
        private static final double IN_HIGHLANDS = -0.4;
        private static final double IN_BASINS = 0.6;
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
