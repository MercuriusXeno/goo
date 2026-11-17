package com.mercuriusxeno.goo.client.ability;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A Spikes cell's bristle layout, drawn from a seed: its pyramids stand
 * inside the cell's top, inset from its edges, each between the shortest
 * and the tallest height, and one seed always draws the same layout
 * (decision urchin-rests-bristled-and-spears).
 */
class SpikesBristleTest {

    private static final long SEED = 90_210L;
    private static final long SEEDS = 200L;

    @Test
    void everyPyramidStandsInsetOnTheCellAtABristlingHeight() {
        for (long seed = 0; seed < SEEDS; seed++) {
            SpikesBristle.Layout layout = SpikesBristle.layoutFromSeed(seed);
            assertEquals(SpikesBristle.PYRAMIDS_PER_CELL, layout.pyramids().size());
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
    void oneSeedAlwaysDrawsTheSameLayout() {
        assertEquals(SpikesBristle.layoutFromSeed(SEED), SpikesBristle.layoutFromSeed(SEED));
    }
}
