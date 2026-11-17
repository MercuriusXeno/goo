package com.mercuriusxeno.goo.ability.crystal;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Where a held stack hovers over its blob cube: Knives' ten knives in an
 * upside-down pyramid of rows 4, 3, 2 and 1 filling the top row left to
 * right first, Dart's three javelins side by side at one height, each
 * spawning at the cube's top and hovering clear above it as the cube shrinks
 * a step per one held (decisions knives-hover-in-an-upside-down-bowling-pin-pyramid,
 * knives-hover-above-the-blob-cube-over-the-hand, knives-blob-shrinks-per-knife-and-vanishes-on-the-last,
 * knives-cube-shrinks-clear-of-each-next-layer, dart-javelins-hover-over-the-shrinking-blob-cube).
 */
class StackHoverTest {

    private static final double DELTA = 1e-9;
    private static final int KNIVES = 10;
    private static final int JAVELINS = 3;

    @Nested
    class KnivesPyramid {

        @Test
        void tenSlotsStandInRowsOfFourThreeTwoAndOneFromTheTopDown() {
            int[][] rows = {{0, 1, 2, 3}, {4, 5, 6}, {7, 8}, {9}};
            double above = Double.MAX_VALUE;
            for (int[] row : rows) {
                double height = StackHover.knifeSlot(row[0]).up();
                assertTrue(height < above, "each row should stand below the one before");
                double midpoint = 0;
                for (int col = 0; col < row.length; col++) {
                    StackHover.Slot slot = StackHover.knifeSlot(row[col]);
                    assertEquals(height, slot.up(), DELTA, "a row's slots share one height");
                    if (col > 0) {
                        assertTrue(slot.right() > StackHover.knifeSlot(row[col - 1]).right(),
                                "a row fills left to right");
                    }
                    midpoint += slot.right() / row.length;
                }
                assertEquals(0, midpoint, DELTA, "each row is centered over the cube");
                above = height;
            }
        }

        @Test
        void theCubeShrinksAStepPerKnifeAndIsGoneAtTheTenth() {
            for (int held = 1; held < KNIVES; held++) {
                assertTrue(StackHover.cubeEdgeAt(held, KNIVES) < StackHover.cubeEdgeAt(held - 1, KNIVES),
                        "the cube at " + held + " knives should be smaller than at " + (held - 1));
            }
            assertEquals(0, StackHover.cubeEdgeAt(KNIVES, KNIVES), DELTA);
        }

        @Test
        void eachKnifeHoversAboveTheCubeAndSpawnsAtItsTop() {
            for (int knife = 1; knife <= KNIVES; knife++) {
                int heldBefore = knife - 1;
                double lowest = StackHover.knifeSlot(knife - 1).up() - StackHover.KNIFE_HALF_HEIGHT;
                double top = StackHover.cubeTopAt(heldBefore, KNIVES);
                assertTrue(lowest > top, "knife " + knife + "'s slot should clear the cube's top " + top
                        + ", lowest at " + lowest);
                StackHover.Slot spawn = StackHover.spawnSlot(heldBefore, KNIVES);
                assertEquals(0, spawn.right(), DELTA);
                assertEquals(top, spawn.up(), DELTA);
            }
        }
    }

    @Nested
    class DartRow {

        @Test
        void threeJavelinsHoverSideBySideAtOneHeightAboveTheCube() {
            double height = StackHover.javelinSlot(0).up();
            for (int javelin = 0; javelin < JAVELINS; javelin++) {
                StackHover.Slot slot = StackHover.javelinSlot(javelin);
                assertEquals(height, slot.up(), DELTA, "the javelins share one height");
                if (javelin > 0) {
                    assertTrue(slot.right() > StackHover.javelinSlot(javelin - 1).right(), "side by side");
                }
                for (int held = 0; held <= JAVELINS; held++) {
                    assertTrue(slot.up() - StackHover.JAVELIN_HALF_HEIGHT > StackHover.cubeTopAt(held, JAVELINS),
                            "a javelin should hover above the cube at " + held + " held");
                }
            }
            assertEquals(StackHover.cubeTopAt(1, JAVELINS), StackHover.spawnSlot(1, JAVELINS).up(), DELTA);
        }

        @Test
        void theCubeShrinksAThirdPerJavelinAndIsGoneAtTheThird() {
            double full = StackHover.cubeEdgeAt(0, JAVELINS);
            assertEquals(full * 2 / 3, StackHover.cubeEdgeAt(1, JAVELINS), DELTA);
            assertEquals(full / 3, StackHover.cubeEdgeAt(2, JAVELINS), DELTA);
            assertEquals(0, StackHover.cubeEdgeAt(JAVELINS, JAVELINS), DELTA);
        }
    }
}
