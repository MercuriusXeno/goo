package com.mercuriusxeno.goo.client.overlay;

import net.minecraft.util.ARGB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The touch reach ring is a closed level circle of the reach's radius around
 * the player's feet (decision mob-ability-touches-at-reach), and the aim line
 * draws a quiet guide whose dashes still scroll (decision
 * aim-line-draws-quieter-so-the-dart-reads).
 */
class ArcRendererTest {

    private static final Vec3 FEET = new Vec3(2, 64, -5);
    private static final double RADIUS = 3;
    private static final int SEGMENTS = 12;
    private static final double TOLERANCE = 1e-9;
    private static final int WHITE = 0xFFFFFF;
    private static final int CORE_PASS = 0;

    @Nested
    class ReachRing {

        @Test
        void ringPointsCloseALevelCircleOfTheReach() {
            Vec3[] points = ArcRenderer.ringPoints(FEET, RADIUS, SEGMENTS);

            assertEquals(SEGMENTS + 1, points.length);
            assertEquals(points[0].x, points[SEGMENTS].x, TOLERANCE);
            assertEquals(points[0].z, points[SEGMENTS].z, TOLERANCE);
            assertAll(java.util.Arrays.stream(points).map(point -> () -> {
                assertEquals(FEET.y, point.y, TOLERANCE);
                assertEquals(RADIUS, Math.hypot(point.x - FEET.x, point.z - FEET.z), TOLERANCE);
            }));
        }

        @Test
        void ringPointsSpreadAroundTheWholeCircle() {
            Vec3[] points = ArcRenderer.ringPoints(FEET, RADIUS, SEGMENTS);

            assertEquals(FEET.x - RADIUS, points[SEGMENTS / 2].x, TOLERANCE);
        }
    }

    @Nested
    class AimLineGlow {

        private final ArcRenderer.GlowPalette palette = ArcRenderer.AIM_LINE_GLOW;

        @Test
        void theCoreDrawsUnderFortyAlpha() {
            assertTrue(ARGB.alpha(ArcRenderer.computeGlowPassColor(WHITE, CORE_PASS, palette)) < 40);
        }

        @Test
        void theHaloStandsAtOnePassOrNone() {
            assertTrue(palette.passes() <= 2);
        }

        @Test
        void eachHaloPassDrawsDimmerThanTheCore() {
            int coreAlpha = ARGB.alpha(ArcRenderer.computeGlowPassColor(WHITE, CORE_PASS, palette));
            for (int pass = 1; pass < palette.passes(); pass++) {
                assertTrue(ARGB.alpha(ArcRenderer.computeGlowPassColor(WHITE, pass, palette)) < coreAlpha);
            }
        }

        @Test
        void theDashesScrollFurtherAtALaterFrame() {
            assertTrue(ArcRenderer.dashOffsetAt(2.0) > ArcRenderer.dashOffsetAt(1.0));
        }
    }
}
