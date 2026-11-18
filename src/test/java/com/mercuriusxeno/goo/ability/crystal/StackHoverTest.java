package com.mercuriusxeno.goo.ability.crystal;

import com.mercuriusxeno.goo.ability.TravelForm;
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
    private static final double CENTERED = 1e-4;

    @Nested
    class KnivesPyramid {

        /** The knives, numbered from 1, in each row from the top down, left to right: the diagonal fill. */
        private final int[][] rows = {{1, 2, 4, 7}, {3, 5, 8}, {6, 9}, {10}};

        @Test
        void neighbouringKnivesStandTwoBladeWidthsApart() {
            StackHover.Slot first = StackHover.knifeSlot(0, 2);
            StackHover.Slot second = StackHover.knifeSlot(1, 2);
            assertEquals(2 * StackHover.KNIFE_WIDTH, second.right() - first.right(), DELTA,
                    "a knife should stand a blade's width clear of its neighbour");
        }

        @Test
        void tenKnivesFillTheRowsByDiagonalsLeftToRight() {
            double above = Double.MAX_VALUE;
            for (int[] row : rows) {
                double height = StackHover.knifeSlot(row[0] - 1, KNIVES).up();
                assertTrue(height < above, "each row should stand below the one before");
                for (int col = 0; col < row.length; col++) {
                    StackHover.Slot slot = StackHover.knifeSlot(row[col] - 1, KNIVES);
                    assertEquals(height, slot.up(), DELTA, "knife " + row[col] + " should share its row's height");
                    if (col > 0) {
                        assertTrue(slot.right() > StackHover.knifeSlot(row[col - 1] - 1, KNIVES).right(),
                                "knife " + row[col] + " should stand right of knife " + row[col - 1]);
                    }
                }
                above = height;
            }
        }

        @Test
        void thePileIsCenteredOverTheHandAtEveryCount() {
            for (int held = 1; held <= KNIVES; held++) {
                double least = Double.MAX_VALUE;
                double greatest = -Double.MAX_VALUE;
                for (int index = 0; index < held; index++) {
                    double right = StackHover.knifeSlot(index, held).right();
                    least = Math.min(least, right);
                    greatest = Math.max(greatest, right);
                }
                assertEquals(0, (least + greatest) / 2, CENTERED, "the pile of " + held + " should be centered");
            }
        }

        @Test
        // knives-blob-shrinks-per-knife-and-vanishes-on-the-last: 9% a knife, stopping at the 10% floor
        void theCubeLosesNinePercentPerKnifeAndStopsAtTheFloor() {
            for (int held = 1; held <= KNIVES; held++) {
                assertTrue(StackHover.cubeEdgeAt(held, KNIVES) < StackHover.cubeEdgeAt(held - 1, KNIVES),
                        "the cube at " + held + " knives should be smaller than at " + (held - 1));
            }
            assertEquals(StackHover.edgeAtFill(0.1), StackHover.cubeEdgeAt(KNIVES, KNIVES), DELTA);
            assertTrue(StackHover.cubeEdgeAt(KNIVES, KNIVES) > 0, "the cube should never vanish");
        }

        @Test
        void eachKnifeHoversAboveTheCubeAndSpawnsAtItsTop() {
            for (int knife = 1; knife <= KNIVES; knife++) {
                int heldBefore = knife - 1;
                double lowest = StackHover.knifeSlot(knife - 1, knife).up() - StackHover.KNIFE_HALF_HEIGHT;
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

        /** The width across a javelin's fins, hovering or thrown alike. */
        private final double width = TravelForm.DART.widthAcrossFins();

        @Test
        void threeJavelinsStandInATriangleOneAndTwoOverThreeMidwayBetween() {
            StackHover.Slot first = StackHover.javelinSlot(0, JAVELINS);
            StackHover.Slot second = StackHover.javelinSlot(1, JAVELINS);
            StackHover.Slot third = StackHover.javelinSlot(2, JAVELINS);
            assertEquals(first.up(), second.up(), DELTA, "javelins 1 and 2 share the upper height");
            assertTrue(second.right() > first.right(), "javelin 2 stands right of javelin 1");
            assertTrue(third.up() < first.up(), "javelin 3 stands under them");
            assertEquals((first.right() + second.right()) / 2, third.right(), DELTA, "javelin 3 midway between");
        }

        @Test
        void thePileIsCenteredOverTheHandAtEveryCount() {
            for (int held = 1; held <= JAVELINS; held++) {
                double least = Double.MAX_VALUE;
                double greatest = -Double.MAX_VALUE;
                for (int index = 0; index < held; index++) {
                    double right = StackHover.javelinSlot(index, held).right();
                    least = Math.min(least, right);
                    greatest = Math.max(greatest, right);
                }
                assertEquals(0, (least + greatest) / 2, CENTERED, "the pile of " + held + " should be centered");
            }
        }

        @Test
        void theSpacingFollowsTheDartsSize() {
            for (int one = 0; one < JAVELINS; one++) {
                for (int other = one + 1; other < JAVELINS; other++) {
                    double apart = distance(StackHover.javelinSlot(one, JAVELINS, width),
                            StackHover.javelinSlot(other, JAVELINS, width));
                    assertTrue(apart >= width, "javelins " + one + " and " + other + " should stand a width "
                            + width + " apart, stand " + apart);
                    double doubled = distance(StackHover.javelinSlot(one, JAVELINS, 2 * width),
                            StackHover.javelinSlot(other, JAVELINS, 2 * width));
                    assertEquals(2 * apart, doubled, DELTA, "a dart twice as large stands them twice as far");
                }
            }
        }

        @Test
        void eachJavelinHoversAboveTheCubeAndSpawnsAtItsTop() {
            for (int javelin = 1; javelin <= JAVELINS; javelin++) {
                double lowest = StackHover.javelinSlot(javelin - 1, javelin).up()
                        - StackHover.javelinHalfHeight(width);
                double top = StackHover.cubeTopAt(javelin - 1, JAVELINS);
                assertTrue(lowest > top, "javelin " + javelin + "'s lowest drawn point should clear the cube's top "
                        + top + ", lowest at " + lowest);
                assertEquals(top, StackHover.spawnSlot(javelin - 1, JAVELINS).up(), DELTA);
            }
        }

        private static double distance(StackHover.Slot one, StackHover.Slot other) {
            return Math.hypot(one.right() - other.right(), one.up() - other.up());
        }

        @Test
        // dart-javelins-hover-over-the-shrinking-blob-cube: 30% a javelin, stopping at the 10% floor
        void theCubeLosesThirtyPercentPerJavelinAndStopsAtTheFloor() {
            assertEquals(StackHover.edgeAtFill(0.7), StackHover.cubeEdgeAt(1, JAVELINS), DELTA);
            assertEquals(StackHover.edgeAtFill(0.4), StackHover.cubeEdgeAt(2, JAVELINS), DELTA);
            assertEquals(StackHover.edgeAtFill(0.1), StackHover.cubeEdgeAt(JAVELINS, JAVELINS), DELTA);
            assertTrue(StackHover.cubeEdgeAt(JAVELINS, JAVELINS) > 0, "the cube should never vanish");
        }
    }

    // palm-blob-and-cutaway-cuboid-size-by-the-buffers-volume: the cube's volume is the fill's share of the full cube's
    @org.junit.jupiter.api.Test
    void theCubesVolumeIsTheFillsShareOfTheFullCubes() {
        for (double fill : new double[]{1.0, 0.7, 0.4, 0.1}) {
            double edge = StackHover.edgeAtFill(fill) / StackHover.FULL_EDGE;
            assertEquals(fill, edge * edge * edge, 1e-6, "the cube at " + fill + " full");
        }
    }

    // knives-cube-shrinks-clear-of-each-next-layer: each held slot clears the cube at the largest fill its count allows
    @org.junit.jupiter.api.Test
    void thePileClearsTheCubeAtEveryCount() {
        for (boolean javelins : new boolean[]{false, true}) {
            int stack = javelins ? JAVELINS : StackHover.KNIVES;
            double halfHeight = javelins
                    ? StackHover.javelinHalfHeight(com.mercuriusxeno.goo.ability.TravelForm.DART.widthAcrossFins())
                    : StackHover.KNIFE_HALF_HEIGHT;
            for (int held = 1; held <= stack; held++) {
                double largestFill = 1 - 0.9 * held / stack;
                double top = StackHover.edgeAtFill(largestFill) / 2;
                for (int index = 0; index < held; index++) {
                    double lowest = StackHover.slotOf(index, held, javelins).up() - halfHeight;
                    assertTrue(lowest > top, (javelins ? "javelin " : "knife ") + index + " at " + held
                            + " held stands its lowest at " + lowest + ", under the cube's top " + top);
                }
            }
        }
    }}
