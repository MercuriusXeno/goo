package com.mercuriusxeno.goo.client.radial;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    /** The fill carries the fluid sprite's pixel at each wheel position (decision wedges-render-fluid-texture). */
    @Nested
    class Fill {

        private static final int SIZE = 64;
        private static final int SPRITE_SIDE = 4;
        private static final int WHITE = 0xFFFFFFFF;
        /** A right half-plane: every sub-sample of a pixel right of center is inside. */
        private static final PetalMask.Shape RIGHT_HALF = (x, y) -> x > 0;

        /** A synthetic sprite whose every pixel is distinct and opaque. */
        private final PetalMask.PixelSource sprite = new PetalMask.PixelSource() {
            @Override
            public int width() {
                return SPRITE_SIDE;
            }

            @Override
            public int height() {
                return SPRITE_SIDE;
            }

            @Override
            public int pixel(int x, int y) {
                return 0xFF000000 | (x * 0x40) << 16 | (y * 0x40) << 8 | 0x11;
            }
        };

        private final int[] pixels = PetalMask.fill(SIZE, RIGHT_HALF, sprite, WHITE);

        @Test
        void insidePixelsCarryTheSpriteAtTheTiledCoordinate() {
            for (int py = 0; py < SIZE; py += 3) {
                for (int px = SIZE / 2; px < SIZE; px += 5) {
                    int tiledX = (px / PetalMask.TEXEL_SCALE) % SPRITE_SIDE;
                    int tiledY = (py / PetalMask.TEXEL_SCALE) % SPRITE_SIDE;
                    assertEquals(sprite.pixel(tiledX, tiledY), pixels[py * SIZE + px],
                            "pixel " + px + "," + py);
                }
            }
        }

        @Test
        void spriteRepeatsAcrossTheWheel() {
            int period = PetalMask.TEXEL_SCALE * SPRITE_SIDE;
            int px = SIZE / 2 + 1;
            assertEquals(pixels[px], pixels[px + period]);
        }

        @Test
        void outsidePixelsStayTransparent() {
            for (int py = 0; py < SIZE; py += 3) {
                for (int px = 0; px < SIZE / 2; px += 5) {
                    assertEquals(0, pixels[py * SIZE + px], "pixel " + px + "," + py);
                }
            }
        }

        @Test
        void tintMultipliesTheSprite() {
            int halfGrey = 0xFF808080;
            int[] tinted = PetalMask.fill(SIZE, RIGHT_HALF, PetalMask.PixelSource.solid(WHITE), halfGrey);
            assertEquals(0xFF808080, tinted[SIZE - 1]);
        }
    }

    /** A petal's boundary carries a solid edge around its fluid fill (decision wedges-take-a-solid-edge). */
    @Nested
    class SolidEdge {

        private static final int SIZE = 256;
        private static final int EDGE_COLOR = 0xFF123456;
        private static final int FILL_COLOR = 0xFFABCDEF;
        private static final double HALF_EDGE = PetalMask.Edge.WEDGE_THICKNESS / 2;
        private static final double MID_RADIUS = (INNER + OUTER) / 2;

        private final int[] pixels = PetalMask.fill(SIZE, new PetalMask.Petal(START, ARC, INNER, OUTER),
                PetalMask.PixelSource.solid(FILL_COLOR), 0xFFFFFFFF,
                new PetalMask.Edge(EDGE_COLOR, PetalMask.Edge.WEDGE_THICKNESS));

        private int pixelAt(double angle, double distance) {
            double half = SIZE / 2.0;
            int px = (int) Math.floor(Math.sin(angle) * distance * half + half);
            int py = (int) Math.floor(-Math.cos(angle) * distance * half + half);
            return pixels[py * SIZE + px];
        }

        /** The angle off a radial edge that puts a point half an edge's width inside it at a radius. */
        private static double halfEdgeOff(double radius) {
            return Math.asin(HALF_EDGE / radius);
        }

        @Test
        void innerArcIsTheEdgeColor() {
            assertEquals(EDGE_COLOR, pixelAt(START + ARC / 2, INNER + HALF_EDGE));
        }

        @Test
        void startEdgeIsTheEdgeColor() {
            assertEquals(EDGE_COLOR, pixelAt(START + halfEdgeOff(MID_RADIUS), MID_RADIUS));
        }

        @Test
        void endEdgeIsTheEdgeColor() {
            assertEquals(EDGE_COLOR, pixelAt(START + ARC - halfEdgeOff(MID_RADIUS), MID_RADIUS));
        }

        @Test
        void roundedCapIsTheEdgeColor() {
            assertEquals(EDGE_COLOR, pixelAt(START + ARC / 2, OUTER - HALF_EDGE));
        }

        @Test
        void interiorCarriesTheFill() {
            assertEquals(FILL_COLOR, pixelAt(START + ARC / 2, MID_RADIUS));
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
