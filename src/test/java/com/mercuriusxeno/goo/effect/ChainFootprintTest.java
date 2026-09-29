package com.mercuriusxeno.goo.effect;

import com.mercuriusxeno.goo.ability.ChainFootprint;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ChainFootprint: shared scaling math for rock and blaze effects.
 */
class ChainFootprintTest {

    private static boolean containsOffset(List<int[]> list, int a, int b) {
        return list.stream().anyMatch(p -> p[0] == a && p[1] == b);
    }

    private static void assertAllUnique(List<int[]> list) {
        Set<String> seen = new HashSet<>();
        for (int[] p : list) {
            assertTrue(seen.add(p[0] + "," + p[1]),
                    "Duplicate offset [" + p[0] + "," + p[1] + "]");
        }
    }

    private static boolean containsOffset3d(List<int[]> list, int x, int y, int z) {
        return list.stream().anyMatch(p -> p[0] == x && p[1] == y && p[2] == z);
    }

    private static Set<String> toSet3d(List<int[]> list) {
        Set<String> set = new HashSet<>();
        for (int[] p : list) {
            set.add(p[0] + "," + p[1] + "," + p[2]);
        }
        return set;
    }

    private static boolean disjoint(Set<String> a, Set<String> b) {
        for (String s : a) {
            if (b.contains(s)) {
                return false;
            }
        }
        return true;
    }

    @Nested
    class Tunnel {
        private static final int[] DEPTHS = {1, 1, 2, 4, 7, 10};
        private static final int[] BLOCKS = {1, 9, 18, 36, 63, 90};

        @Test
        void depthReadsTheLadderOneOneTwoFourSevenTen() {
            for (int s = 1; s <= DEPTHS.length; s++) {
                assertEquals(DEPTHS[s - 1], ChainFootprint.tunnelDepth(s), "depth at stacks=" + s);
            }
        }

        @Test
        void boresOneNineEighteenThirtySixSixtyThreeNinety() {
            for (int s = 1; s <= BLOCKS.length; s++) {
                assertEquals(BLOCKS[s - 1], ChainFootprint.totalBlocks(s), "blocks at stacks=" + s);
            }
        }

        @Test
        void oneStackIsTheSingleBlock() {
            List<int[]> fp = ChainFootprint.layerFootprint(1);
            assertEquals(1, fp.size());
            assertArrayEquals(new int[]{0, 0}, fp.getFirst());
        }

        @Test
        void twoStacksOnIsTheFullThreeByThree() {
            for (int s = 2; s <= DEPTHS.length; s++) {
                List<int[]> fp = ChainFootprint.layerFootprint(s);
                assertEquals(9, fp.size(), "layer cells at stacks=" + s);
                assertAllUnique(fp);
                for (int a = -1; a <= 1; a++) {
                    for (int b = -1; b <= 1; b++) {
                        assertTrue(containsOffset(fp, a, b), "missing [" + a + "," + b + "] at stacks=" + s);
                    }
                }
            }
        }

        @Test
        void ladderEndsAtSixStacksTenDeep() {
            assertEquals(6, ChainFootprint.MAX_STACKS);
            assertEquals(10, ChainFootprint.MAX_DEPTH);
            assertEquals(10, ChainFootprint.tunnelDepth(9));
        }
    }

    @Nested
    class FlatDisc {
        @Test
        void ladderReadsOneNineTwentyOneThirtySevenSixtyNineNinetySeven() {
            int[] ladder = {1, 9, 21, 37, 69, 97};
            for (int s = 1; s <= ladder.length; s++) {
                assertEquals(ladder[s - 1], ChainFootprint.flatFootprint(s).size(), "disc cells at stacks=" + s);
            }
        }

        @Test
        void holdsEveryCellCenteredUnderRadiusPlusHalfAndNoOther() {
            for (int s = 1; s <= 6; s++) {
                List<int[]> disc = ChainFootprint.flatFootprint(s);
                assertAllUnique(disc);
                double reach = (s - 1) + 0.5;
                int span = s + 1;
                for (int a = -span; a <= span; a++) {
                    for (int b = -span; b <= span; b++) {
                        assertEquals(Math.hypot(a, b) < reach, containsOffset(disc, a, b),
                                "cell [" + a + "," + b + "] at stacks=" + s);
                    }
                }
            }
        }

        @Test
        void startRadiusShiftsTheLadder() {
            assertEquals(ChainFootprint.flatFootprint(4).size(), ChainFootprint.flatFootprint(1, 3).size());
            assertEquals(ChainFootprint.flatFootprint(6).size(), ChainFootprint.flatFootprint(3, 3).size());
        }
    }

    @Nested
    class FlatRings {
        private static Set<String> keys(List<int[]> cells) {
            Set<String> set = new HashSet<>();
            for (int[] p : cells) {
                set.add(p[0] + "," + p[1]);
            }
            return set;
        }

        @Test
        void ringsDoNotOverlapAndUniteToTheDisc() {
            for (int s = 2; s <= 6; s++) {
                Set<String> union = new HashSet<>();
                for (List<int[]> ring : ChainFootprint.flatRings(s)) {
                    for (int[] p : ring) {
                        assertTrue(union.add(p[0] + "," + p[1]), "duplicate across rings at stacks=" + s);
                    }
                }
                assertEquals(keys(ChainFootprint.flatFootprint(s)), union, "ring union at stacks=" + s);
            }
        }

        @Test
        void ringsStepOutwardByIntegerDistance() {
            for (int s = 2; s <= 6; s++) {
                List<List<int[]>> rings = ChainFootprint.flatRings(s);
                assertEquals(s, rings.size(), "ring count at stacks=" + s);
                for (int k = 0; k < rings.size(); k++) {
                    for (int[] p : rings.get(k)) {
                        assertEquals(k, (int) Math.floor(Math.hypot(p[0], p[1])),
                                "ring " + k + " holds [" + p[0] + "," + p[1] + "] at stacks=" + s);
                    }
                }
            }
        }

        @Test
        void firstRingIsTheCenterAlone() {
            List<int[]> center = ChainFootprint.flatRings(6).getFirst();
            assertEquals(1, center.size());
            assertArrayEquals(new int[]{0, 0}, center.getFirst());
        }
    }

    @Nested
    class SphereShell {
        @Test
        void radius0IsOriginOnly() {
            List<int[]> shell = ChainFootprint.sphereShell(0);
            assertEquals(1, shell.size());
            assertTrue(containsOffset3d(shell, 0, 0, 0));
        }

        @Test
        void radius1HasSixCardinals() {
            List<int[]> shell = ChainFootprint.sphereShell(1);
            assertTrue(containsOffset3d(shell, 1, 0, 0));
            assertTrue(containsOffset3d(shell, -1, 0, 0));
            assertTrue(containsOffset3d(shell, 0, 1, 0));
            assertTrue(containsOffset3d(shell, 0, -1, 0));
            assertTrue(containsOffset3d(shell, 0, 0, 1));
            assertTrue(containsOffset3d(shell, 0, 0, -1));
            assertFalse(containsOffset3d(shell, 0, 0, 0), "origin should not be in shell 1");
        }

        @Test
        void shellsDoNotOverlap() {
            Set<String> r0 = toSet3d(ChainFootprint.sphereShell(0));
            Set<String> r1 = toSet3d(ChainFootprint.sphereShell(1));
            Set<String> r2 = toSet3d(ChainFootprint.sphereShell(2));
            assertTrue(disjoint(r0, r1), "shell 0 and 1 overlap");
            assertTrue(disjoint(r1, r2), "shell 1 and 2 overlap");
            assertTrue(disjoint(r0, r2), "shell 0 and 2 overlap");
        }

        @Test
        void shellsUnionEqualsSolid() {
            int radius = 3;
            Set<String> union = new HashSet<>();
            for (int r = 0; r <= radius; r++) {
                union.addAll(toSet3d(ChainFootprint.sphereShell(r)));
            }
            int r2 = radius * radius;
            int solidCount = 0;
            for (int x = -radius; x <= radius; x++) {
                for (int y = -radius; y <= radius; y++) {
                    for (int z = -radius; z <= radius; z++) {
                        if (x * x + y * y + z * z <= r2) {
                            solidCount++;
                        }
                    }
                }
            }
            assertEquals(solidCount, union.size(),
                    "union of shells 0.." + radius + " should equal solid sphere");
        }

        @Test
        void allUniqueWithinShell() {
            for (int r = 0; r <= 4; r++) {
                List<int[]> shell = ChainFootprint.sphereShell(r);
                Set<String> seen = new HashSet<>();
                for (int[] p : shell) {
                    assertTrue(seen.add(p[0] + "," + p[1] + "," + p[2]),
                            "duplicate in shell " + r);
                }
            }
        }
    }
}
