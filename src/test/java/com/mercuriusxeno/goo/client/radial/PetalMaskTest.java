package com.mercuriusxeno.goo.client.radial;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Covers a wedge mask's petal shape: a rounded cap at its outer end in place of a circle cut (decision wedges-round-off-like-petals). */
class PetalMaskTest {

    private static final double ARC = 2.0 * Math.PI / 16;
    private static final double START = 3 * ARC;
    private static final double INNER = RadialWheel.HUB_FRACTION;
    private static final double OUTER = 1.0;
    private static final double JUST_INSIDE = 0.001;

    private static boolean insideAt(double angle, double distance) {
        return PetalMask.isInsidePetal(Math.sin(angle) * distance, -Math.cos(angle) * distance,
                START, ARC, INNER, OUTER);
    }

    @Nested
    class OuterEnd {

        @Test
        void centerAngleReachesTheOuterRadius() {
            assertTrue(insideAt(START + ARC / 2, OUTER - JUST_INSIDE));
        }

        @Test
        void startEdgeFallsShortOfTheWheelCircle() {
            assertFalse(insideAt(START + JUST_INSIDE, OUTER - JUST_INSIDE));
        }

        @Test
        void endEdgeFallsShortOfTheWheelCircle() {
            assertFalse(insideAt(START + ARC - JUST_INSIDE, OUTER - JUST_INSIDE));
        }

        @Test
        void pointPastTheOuterRadiusIsOutside() {
            assertFalse(insideAt(START + ARC / 2, OUTER + JUST_INSIDE));
        }
    }

    @Nested
    class Body {

        @Test
        void pointInsideTheInnerRadiusIsOutside() {
            assertFalse(insideAt(START + ARC / 2, INNER - JUST_INSIDE));
        }

        @Test
        void edgeNearTheInnerRadiusIsInside() {
            assertTrue(insideAt(START + JUST_INSIDE, INNER + JUST_INSIDE));
        }

        @Test
        void angleOutsideTheWedgeIsOutside() {
            assertFalse(insideAt(START - JUST_INSIDE, (INNER + OUTER) / 2));
        }
    }

    @Nested
    class HalfTurnWedge {

        @Test
        void keepsTheCircleCutAtItsEdges() {
            double halfTurn = Math.PI;
            double angle = JUST_INSIDE;
            assertTrue(PetalMask.isInsidePetal(Math.sin(angle) * (OUTER - JUST_INSIDE),
                    -Math.cos(angle) * (OUTER - JUST_INSIDE), 0.0, halfTurn, INNER, OUTER));
        }
    }
}
