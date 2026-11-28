package com.mercuriusxeno.goo.lunar;

import net.minecraft.core.BlockPos;
import net.minecraft.util.valueproviders.UniformInt;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * No two lunar features share a shape: two neighbouring giant craters differ in radius, two neighbouring basin pools
 * differ in reach, and two boulders from different positions differ in size and silhouette; one crater cell or one
 * boulder position always draws the same shape. The rolls are a pure hash standing in for the world's
 * seeded noise.
 * decision no-two-lunar-features-look-alike
 */
class LunarFeatureShapesTest {

    /** Two craters' radii, or two pools' reaches, differ past this many blocks. */
    private static final double RADIUS_TOLERANCE = 0.25;
    /** Two boulders' radii differ past this many blocks. */
    private static final double BOULDER_SIZE_TOLERANCE = 0.1;
    private static final long WORLD_SEED = 1_234_567L;

    /** A pure hash of a cell and a roll onto [0, 1), standing in for the seeded noise's rolls. */
    private static double hashRoll(int cellX, int cellZ, int roll) {
        long hash = cellX * 3_129_871L ^ cellZ * 116_129_781L ^ roll * 0x2545F4914F6CDD1DL;
        hash ^= hash >>> 29;
        hash *= 0x9E3779B97F4A7C15L;
        hash ^= hash >>> 32;
        return (hash >>> 11) * 0x1.0p-53;
    }

    /** Biome noises a crater centred outside maria and the basins reads. */
    private static final CentreGround HIGHLANDS = new FixedGround(-0.3, -0.4);

    @Nested
    class Craters {

        @Test
        void neighbouringGiantCratersDifferInRadius() {
            GiantCraterField.Crater west = GiantCraterField.craterIn(0, 0, LunarFeatureShapesTest::hashRoll, HIGHLANDS);
            GiantCraterField.Crater east = GiantCraterField.craterIn(1, 0, LunarFeatureShapesTest::hashRoll, HIGHLANDS);
            assertTrue(Math.abs(west.radius() - east.radius()) > RADIUS_TOLERANCE,
                    "radii " + west.radius() + " and " + east.radius());
        }

        @Test
        void oneCellAlwaysDrawsOneGiantCrater() {
            assertEquals(GiantCraterField.craterIn(3, 5, LunarFeatureShapesTest::hashRoll, HIGHLANDS),
                    GiantCraterField.craterIn(3, 5, LunarFeatureShapesTest::hashRoll, HIGHLANDS));
        }
    }

    @Nested
    class BasinPoolSites {

        /** The first pair of east-west neighbouring cells along a row that both hold a pool site on the basin floor. */
        private BasinPools.Site[] neighbouringSites() {
            for (int cellX = 0; cellX < 1000; cellX++) {
                Optional<BasinPools.Site> west = BasinPools.siteIn(cellX, 0, LunarFeatureShapesTest::hashRoll,
                        (x, z) -> 1.0);
                Optional<BasinPools.Site> east = BasinPools.siteIn(cellX + 1, 0, LunarFeatureShapesTest::hashRoll,
                        (x, z) -> 1.0);
                if (west.isPresent() && east.isPresent()) {
                    return new BasinPools.Site[] {west.get(), east.get()};
                }
            }
            return null;
        }

        @Test
        void neighbouringPoolsDifferInReach() {
            BasinPools.Site[] pair = neighbouringSites();
            assertNotNull(pair, "no two neighbouring cells hold a pool site");
            assertTrue(Math.abs(pair[0].reach() - pair[1].reach()) > RADIUS_TOLERANCE,
                    "reaches " + pair[0].reach() + " and " + pair[1].reach());
        }

        @Test
        void aSiteOffTheBasinFloorHoldsNoPool() {
            BasinPools.Site[] pair = neighbouringSites();
            assertNotNull(pair, "no two neighbouring cells hold a pool site");
            int cellX = Math.floorDiv((int) pair[0].centerX(), BasinPools.CELL);
            assertTrue(BasinPools.siteIn(cellX, 0, LunarFeatureShapesTest::hashRoll,
                    (x, z) -> BasinPools.BASIN_LOW_FLOOR - 0.01).isEmpty());
        }
    }

    @Nested
    class Boulders {

        private final UniformInt radii = UniformInt.of(5, 10);
        private final BlockPos first = new BlockPos(100, 64, 100);
        private final BlockPos second = new BlockPos(100, 64, 164);

        /** Which columns a boulder's shape stands over, and how tall it stands over each: its silhouette. */
        private Set<BlockPos> silhouette(BoulderFeature.Shape shape) {
            Set<BlockPos> columns = new HashSet<>();
            int reach = (int) Math.ceil(shape.radius() + shape.knobRadius());
            for (int dx = -reach; dx <= reach; dx++) {
                for (int dz = -reach; dz <= reach; dz++) {
                    for (int dy = reach; dy >= -reach; dy--) {
                        if (shape.holds(dx, dy, dz)) {
                            columns.add(new BlockPos(dx, dy, dz));
                            break;
                        }
                    }
                }
            }
            return columns;
        }

        @Test
        void bouldersFromTwoPositionsDifferInSizeAndSilhouette() {
            BoulderFeature.Shape one = BoulderFeature.shapeAt(first, WORLD_SEED, radii);
            BoulderFeature.Shape two = BoulderFeature.shapeAt(second, WORLD_SEED, radii);
            assertTrue(Math.abs(one.radius() - two.radius()) > BOULDER_SIZE_TOLERANCE,
                    "radii " + one.radius() + " and " + two.radius());
            assertTrue(!silhouette(one).equals(silhouette(two)), "the two boulders share a silhouette");
        }

        @Test
        void onePositionAlwaysDrawsOneBoulder() {
            assertEquals(silhouette(BoulderFeature.shapeAt(first, WORLD_SEED, radii)),
                    silhouette(BoulderFeature.shapeAt(first, WORLD_SEED, radii)));
        }
    }
}
