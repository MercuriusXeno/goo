package com.mercuriusxeno.goo.client.ability;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Released weapons return to the palm blob one after another, a start every
 * 2 ticks in the reverse of the order they formed, each 4 to 5 ticks from its
 * start to its merge, turning from the weapon back into its cuboid on the way.
 * decision released-weapons-turn-back-into-blobs-that-rejoin-the-palm-blob
 */
class StackReturnTest {

    private static final int TEN = 10;
    private static final int THREE = 3;

    @Test
    void tenKnivesReturnTheLastFormedFirstAndAreAllBackWithinTwentyThreeTicks() {
        assertEquals(0, StackReturn.startOf(TEN - 1, TEN), 1e-9, "the tenth starts at the release");
        assertEquals(18, StackReturn.startOf(0, TEN), 1e-9, "the first starts 18 ticks after it");
        for (int index = 0; index < TEN; index++) {
            assertEquals(1f, StackReturn.progressAt(index, TEN, 23), 1e-6f, "knife " + index + " back by 23 ticks");
            double start = StackReturn.startOf(index, TEN);
            assertEquals(0f, StackReturn.progressAt(index, TEN, start), 1e-6f, "knife " + index + " starts then");
            assertTrue(StackReturn.progressAt(index, TEN, start + 4) < 1f, "knife " + index + " takes over 4 ticks");
            assertEquals(1f, StackReturn.progressAt(index, TEN, start + 5), 1e-6f, "and 5 at most");
        }
    }

    @Test
    void threeJavelinsAreBackWithinNineTicks() {
        assertEquals(4, StackReturn.startOf(0, THREE), 1e-9);
        assertEquals(1f, StackReturn.progressAt(0, THREE, 9), 1e-6f);
    }

    @Test
    void aReturningWeaponOnlyFallsBackToItsCuboid() {
        assertEquals(1f, StackReturn.weaponWeightAt(0f), 1e-6f, "whole as it leaves its slot");
        assertEquals(0f, StackReturn.weaponWeightAt(1f), 1e-6f, "all cuboid as it merges");
        float before = 2f;
        for (float progress = 0f; progress <= 1f; progress += 0.05f) {
            float weight = StackReturn.weaponWeightAt(progress);
            assertTrue(weight <= before, "the weapon's weight should only fall, " + weight + " after " + before);
            before = weight;
        }
    }
}