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
 * No two lunar features share a shape: two neighbouring craters differ in radius, in the outline their rims trace
 * round the circle and in wear, and two boulders from different positions differ in size and silhouette; one crater
 * cell or one boulder position always draws the same shape. The rolls are a pure hash standing in for the world's
 * seeded noise.
 * decision no-two-lunar-features-look-alike
 */
class LunarFeatureShapesTest {

    /** Two craters' radii differ past this many blocks. */
    private static final double RADIUS_TOLERANCE = 0.25;
    /** Two rims' outlines, each read as its radius over its mean round the circle, differ past this share somewhere. */
    private static final double OUTLINE_TOLERANCE = 0.01;
    /** Two craters' wear differs past this. */
    private static final double WEAR_TOLERANCE = 0.02;
    /** Two boulders' radii differ past this many blocks. */
    private static final double BOULDER_SIZE_TOLERANCE = 0.1;
    private static final int OUTLINE_SAMPLES = 16;
    private static final long WORLD_SEED = 1_234_567L;

    /** A pure hash of a cell and a roll onto [0, 1), standing in for the seeded noise's rolls. */
    private static double hashRoll(int cellX, int cellZ, int roll) {
        long hash = cellX * 3_129_871L ^ cellZ * 116_129_781L ^ roll * 0x2545F4914F6CDD1DL;
        hash ^= hash >>> 29;
        hash *= 0x9E3779B97F4A7C15L;
        hash ^= hash >>> 32;
        return (hash >>> 11) * 0x1.0p-53;
    }

    /** The first pair of east-west neighbouring cells along a row that both hold a crater. */
    private static CraterField.Crater[] neighbouringCraters() {
        for (int cellX = 0; cellX < 1000; cellX++) {
            Optional<CraterField.Crater> west = CraterField.craterIn(cellX, 0, LunarFeatureShapesTest::hashRoll);
            Optional<CraterField.Crater> east = CraterField.craterIn(cellX + 1, 0, LunarFeatureShapesTest::hashRoll);
            if (west.isPresent() && east.isPresent()) {
                return new CraterField.Crater[] {west.get(), east.get()};
            }
        }
        return null;
    }

    private static double outlineGap(CraterField.Crater first, CraterField.Crater second) {
        double widest = 0.0;
        for (int sample = 0; sample < OUTLINE_SAMPLES; sample++) {
            double angle = 2.0 * Math.PI * sample / OUTLINE_SAMPLES;
            widest = Math.max(widest, Math.abs(first.rimRadiusAt(angle) / first.radius()
                    - second.rimRadiusAt(angle) / second.radius()));
        }
        return widest;
    }

    @Nested
    class Craters {

        @Test
        void neighbouringCratersDifferInRadiusOutlineAndWear() {
            CraterField.Crater[] pair = neighbouringCraters();
            assertNotNull(pair, "no two neighbouring cells hold a crater");
            assertTrue(Math.abs(pair[0].radius() - pair[1].radius()) > RADIUS_TOLERANCE,
                    "radii " + pair[0].radius() + " and " + pair[1].radius());
            assertTrue(outlineGap(pair[0], pair[1]) > OUTLINE_TOLERANCE, "outlines differ by "
                    + outlineGap(pair[0], pair[1]));
            assertTrue(Math.abs(pair[0].wear() - pair[1].wear()) > WEAR_TOLERANCE,
                    "wear " + pair[0].wear() + " and " + pair[1].wear());
        }

        @Test
        void aCratersRimSwellsAndPinchesRoundItsCircle() {
            CraterField.Crater crater = neighbouringCraters()[0];
            double least = Double.MAX_VALUE;
            double most = 0.0;
            for (int sample = 0; sample < OUTLINE_SAMPLES; sample++) {
                double rim = crater.rimRadiusAt(2.0 * Math.PI * sample / OUTLINE_SAMPLES);
                least = Math.min(least, rim);
                most = Math.max(most, rim);
            }
            assertTrue(most - least > crater.radius() * OUTLINE_TOLERANCE, "rim runs " + least + " to " + most);
        }

        @Test
        void oneCellAlwaysDrawsOneCrater() {
            assertEquals(CraterField.craterIn(3, 5, LunarFeatureShapesTest::hashRoll),
                    CraterField.craterIn(3, 5, LunarFeatureShapesTest::hashRoll));
        }

        @Test
        void aWornCraterSinksLessThanAFreshOne() {
            double radius = 10.0;
            assertTrue(CraterField.profile(0.0, radius, 0.9, 1.0) > CraterField.profile(0.0, radius, 0.0, 1.0));
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
