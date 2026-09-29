package com.mercuriusxeno.goo.client.overlay;

import com.mercuriusxeno.goo.client.TargetResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Each ripple ring grows from the face center to the face's edge and fades to
 * nothing as it meets the edge, and staggered rings show at once (decision
 * ripple-rings-fade-to-face-edge).
 */
class RippleRingsTest {

    private static final double EPSILON = 1e-9;
    private static final int SAMPLES = 200;
    private static final BlockPos POS = new BlockPos(-5, 70, 12);

    @Nested
    class Radius {
        @Test
        void bornAtTheFaceCenter() {
            assertEquals(0, RippleRings.ringRadius(0), EPSILON);
        }

        @Test
        void reachesHalfABlockAtTheEndOfItsLife() {
            assertEquals(0.5, RippleRings.ringRadius(1), EPSILON);
        }

        @Test
        void growsAsThePhaseAdvances() {
            for (int i = 1; i <= SAMPLES; i++) {
                double before = RippleRings.ringRadius((i - 1.0) / SAMPLES);
                double after = RippleRings.ringRadius((double) i / SAMPLES);
                assertTrue(after > before, "radius at sample " + i);
            }
        }

        @Test
        void drawsNothingOnceItMeetsTheEdge() {
            assertTrue(RippleRings.isAlive(0));
            assertTrue(RippleRings.isAlive(0.999));
            assertFalse(RippleRings.isAlive(1));
            assertFalse(RippleRings.isAlive(1.2));
        }
    }

    @Nested
    class Opacity {
        @Test
        void peaksAtBirth() {
            assertEquals(1, RippleRings.ringOpacity(0), EPSILON);
        }

        @Test
        void neverRisesAsThePhaseAdvances() {
            for (int i = 1; i <= SAMPLES; i++) {
                double before = RippleRings.ringOpacity((i - 1.0) / SAMPLES);
                double after = RippleRings.ringOpacity((double) i / SAMPLES);
                assertTrue(after <= before, "opacity at sample " + i);
            }
        }

        @Test
        void isZeroWhereTheRadiusMeetsTheEdge() {
            assertEquals(0.5, RippleRings.ringRadius(1), EPSILON);
            assertEquals(0, RippleRings.ringOpacity(1), EPSILON);
        }
    }

    @ParameterizedTest
    @EnumSource(Direction.class)
    void noRingPointLeavesTheFaceSquare(Direction face) {
        Vec3 faceCenter = new TargetResult.BlockTarget(POS, face, false).resolveEndpoint();
        for (int s = 0; s <= SAMPLES; s++) {
            double now = RippleRings.PERIOD_SECONDS * s / SAMPLES;
            for (double phase : RippleRings.ringPhases(now)) {
                if (!RippleRings.isAlive(phase)) {
                    continue;
                }
                Vec3[] ring = FaceBullseyeRenderer.ringPoints(faceCenter, face, RippleRings.ringRadius(phase),
                        FaceBullseyeRenderer.RING_SEGMENTS);
                for (Vec3 point : ring) {
                    for (Direction.Axis axis : Direction.Axis.values()) {
                        if (axis != face.getAxis()) {
                            double coord = point.get(axis);
                            double min = POS.get(axis);
                            assertTrue(coord >= min - EPSILON && coord <= min + 1 + EPSILON,
                                    () -> face + " point " + point + " leaves the block on " + axis);
                        }
                    }
                }
            }
        }
    }

    @Test
    void severalConcentricRingsShowAtOnce() {
        for (int s = 0; s <= SAMPLES; s++) {
            double now = RippleRings.PERIOD_SECONDS * s / SAMPLES;
            double[] radii = Arrays.stream(RippleRings.ringPhases(now))
                    .filter(RippleRings::isAlive)
                    .map(RippleRings::ringRadius)
                    .distinct()
                    .toArray();
            assertTrue(radii.length >= 2, "rings alive at " + now + ": " + Arrays.toString(radii));
        }
    }
}
