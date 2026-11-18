package com.mercuriusxeno.goo.client.ability;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A Spikes cell's bristle layout, drawn from a seed: its pyramids stand
 * inside the cell's top, inset from its edges, each between the shortest
 * and the tallest height, no two closer than the least spacing, within the
 * cell or against a neighbouring cell's, and one seed always draws the same
 * layout; each pyramid grows out of the blob's midline, and its three faces
 * shade apart under the fixed light
 * (decision urchin-rests-bristled-and-spears; operator UAT 2026-11-17).
 */
class SpikesBristleTest {

    private static final long SEED = 90_210L;
    private static final long SEEDS = 200L;
    /** The share of the inset span the first pyramids of many cells must cover between them. */
    private static final double SPREAD_SHARE = 0.8;

    @Test
    void everyPyramidStandsInsetOnTheCellAtABristlingHeight() {
        for (long seed = 0; seed < SEEDS; seed++) {
            SpikesBristle.Layout layout = SpikesBristle.layoutFromSeed(seed);
            int count = layout.pyramids().size();
            assertTrue(count >= SpikesBristle.FEWEST_PYRAMIDS && count <= SpikesBristle.MOST_PYRAMIDS, "count " + count);
            for (SpikesBristle.Pyramid pyramid : layout.pyramids()) {
                assertTrue(pyramid.x() >= SpikesBristle.EDGE_INSET && pyramid.x() <= 1f - SpikesBristle.EDGE_INSET,
                        "x " + pyramid.x());
                assertTrue(pyramid.z() >= SpikesBristle.EDGE_INSET && pyramid.z() <= 1f - SpikesBristle.EDGE_INSET,
                        "z " + pyramid.z());
                assertTrue(pyramid.height() >= SpikesBristle.LEAST_HEIGHT
                        && pyramid.height() <= SpikesBristle.LEAST_HEIGHT + SpikesBristle.HEIGHT_SPREAD,
                        "height " + pyramid.height());
            }
        }
    }

    @Test
    void cellsDrawDifferentCountsAndDifferentSpots() {
        java.util.Set<Integer> counts = new java.util.HashSet<>();
        for (long seed = 0; seed < SEEDS; seed++) {
            counts.add(SpikesBristle.layoutFromSeed(seed).pyramids().size());
        }
        assertEquals(SpikesBristle.MOST_PYRAMIDS - SpikesBristle.FEWEST_PYRAMIDS + 1, counts.size(),
                "counts drawn " + counts);
        double least = 1;
        double most = 0;
        for (long seed = 0; seed < SEEDS; seed++) {
            float x = SpikesBristle.layoutFromSeed(seed).pyramids().getFirst().x();
            least = Math.min(least, x);
            most = Math.max(most, x);
        }
        double inset = 1 - SpikesBristle.EDGE_INSET - SpikesBristle.EDGE_INSET;
        assertTrue(most - least > SPREAD_SHARE * inset, "first pyramids span only " + least + " to " + most);
        assertNotEquals(SpikesBristle.layoutFromSeed(SEED), SpikesBristle.layoutFromSeed(SEED + 1));
    }

    @Test
    void noTwoPyramidsStandCloserThanTheLeastSpacingAcrossNeighbouringCells() {
        for (long seed = 0; seed < SEEDS; seed++) {
            SpikesBristle.Layout here = SpikesBristle.layoutFromSeed(seed);
            SpikesBristle.Layout next = SpikesBristle.layoutFromSeed(seed + SEEDS);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    for (SpikesBristle.Pyramid one : here.pyramids()) {
                        for (SpikesBristle.Pyramid other : (dx == 0 && dz == 0 ? here : next).pyramids()) {
                            if (one == other) {
                                continue;
                            }
                            double gap = Math.hypot(other.x() + dx - one.x(), other.z() + dz - one.z());
                            assertTrue(gap >= SpikesBristle.LEAST_SPACING, "seed " + seed + " gap " + gap);
                        }
                    }
                }
            }
        }
    }

    @Test
    void aPyramidGrowsOutOfTheBlobsMidline() {
        assertEquals(64.0 + 0.5 * 9 / 15, SpikesBristle.midline(new net.minecraft.core.BlockPos(0, 64, 0), 9),
                1e-9);
    }

    @Test
    void thePyramidsThreeFacesShadeApartAndTheUpperFaceIsBrightest() {
        int[] tones = SpikesBristle.faceTones();
        assertEquals(SpikesBristle.PYRAMID_SIDES, tones.length);
        for (int one = 0; one < tones.length; one++) {
            for (int other = one + 1; other < tones.length; other++) {
                assertTrue(Math.abs(brightness(tones[one]) - brightness(tones[other])) >= SpikesBristle.LEAST_FACE_CONTRAST,
                        "faces " + one + " and " + other + " shade too alike");
            }
        }
    }

    private static int brightness(int rgb) {
        return ((rgb >> 16) & 0xFF) + ((rgb >> 8) & 0xFF) + (rgb & 0xFF);
    }

    @Test
    void oneSeedAlwaysDrawsTheSameLayout() {
        assertEquals(SpikesBristle.layoutFromSeed(SEED), SpikesBristle.layoutFromSeed(SEED));
    }
}
