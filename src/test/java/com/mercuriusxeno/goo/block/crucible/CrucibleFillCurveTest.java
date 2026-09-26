package com.mercuriusxeno.goo.block.crucible;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the crucible's shared rise curve: the level holds at the puddle depth
 * until the spread volume, then rises, each pixel taking more volume than the
 * one below it, and keeps moving until the rim volume.
 */
class CrucibleFillCurveTest {

    private static final float ONE_PIXEL = 1f / CrucibleBasin.PIXEL_COUNT;
    private static final float PIXEL_HEIGHT = 1f / 16f;
    /** A full stack of storage blocks of the densest goo value melted: 64 x 9 x 13824 mB. */
    private static final int DENSE_STACK_VOLUME = 64 * 9 * 13_824;
    /** A volume far past that stack, twelve stacks over. */
    private static final int FAR_PAST_A_DENSE_STACK = DENSE_STACK_VOLUME * 12;

    @Test
    void emptyCrucibleHasNoFill() {
        assertEquals(0f, CrucibleBasin.fillFraction(0));
        assertEquals(0f, CrucibleBasin.fillFraction(-100));
        assertEquals(0f, CrucibleBasin.heightFraction(0));
    }

    @Test
    void levelHoldsUntilThePuddleTouchesTheWalls() {
        assertEquals(1_000, CrucibleBasin.SPREAD_VOLUME);
        for (int volume = 50; volume <= CrucibleBasin.SPREAD_VOLUME; volume += 50) {
            assertEquals(0f, CrucibleBasin.fillFraction(volume), volume + " mB rose before the walls");
        }
        assertTrue(CrucibleBasin.fillFraction(CrucibleBasin.SPREAD_VOLUME + 1) > 0f);
    }

    @Test
    void moderateVolumeStandsOneToTwoPixelsUp() {
        float pixelsUp = (CrucibleBasin.surfaceYForVolume(16_000) - CrucibleBasin.FLOOR_Y) / PIXEL_HEIGHT;
        assertTrue(pixelsUp >= 1f && pixelsUp <= 2f, "16000 mB stands " + pixelsUp + " pixels up");
    }

    @Test
    void eachPixelTakesMoreVolumeThanTheOneBelow() {
        double previousGap = 0;
        double previousCrossing = CrucibleBasin.SPREAD_VOLUME;
        for (int pixel = 1; pixel <= CrucibleBasin.PIXEL_COUNT; pixel++) {
            double crossing = CrucibleBasin.pixelCrossingVolume(pixel);
            double gap = crossing - previousCrossing;
            assertTrue(gap > previousGap, "pixel " + pixel + " took " + gap + " mB, not more than " + previousGap);
            previousGap = gap;
            previousCrossing = crossing;
        }
    }

    @Test
    void pixelCrossingsLandOnThePixelLines() {
        for (int pixel = 1; pixel < CrucibleBasin.PIXEL_COUNT; pixel++) {
            long volume = Math.round(CrucibleBasin.pixelCrossingVolume(pixel));
            assertEquals(pixel * ONE_PIXEL, CrucibleBasin.heightFraction(volume), 1e-4f, "pixel " + pixel);
        }
    }

    @Test
    void lastPixelCrossingIsTheRimVolume() {
        assertEquals(CrucibleBasin.RIM_VOLUME, CrucibleBasin.pixelCrossingVolume(CrucibleBasin.PIXEL_COUNT), 1.0);
    }

    @Test
    void levelRisesStrictlyOverAGeometricSweepPastTheSpread() {
        float previousFill = 0f;
        float previousY = CrucibleBasin.surfaceYForVolume(CrucibleBasin.SPREAD_VOLUME);
        for (long volume = CrucibleBasin.SPREAD_VOLUME + 1; volume < CrucibleBasin.RIM_VOLUME;
                volume = volume * 3 / 2 + 1) {
            float fill = CrucibleBasin.fillFraction(volume);
            float surfaceY = CrucibleBasin.surfaceYForVolume(volume);
            assertTrue(fill > previousFill, "fill held at " + fill + " by " + volume + " mB");
            assertTrue(surfaceY > previousY, "surface held at " + surfaceY + " by " + volume + " mB");
            previousFill = fill;
            previousY = surfaceY;
        }
    }

    @Test
    void levelStillMovesFarPastADenseStack() {
        float stack = CrucibleBasin.fillFraction(DENSE_STACK_VOLUME);
        float farPast = CrucibleBasin.fillFraction(FAR_PAST_A_DENSE_STACK);
        assertTrue(farPast > stack);
        assertTrue(farPast < 1f, "fill saturated at " + FAR_PAST_A_DENSE_STACK + " mB");
    }

    @Test
    void onlyTheRimVolumeFillsTheBasin() {
        assertEquals(1_000_000_000, CrucibleBasin.RIM_VOLUME);
        assertTrue(CrucibleBasin.fillFraction(CrucibleBasin.RIM_VOLUME - 1) < 1f);
        assertTrue(CrucibleBasin.heightFraction(CrucibleBasin.RIM_VOLUME - 1) < 1f);
        assertEquals(1f, CrucibleBasin.heightFraction(CrucibleBasin.RIM_VOLUME));
        assertEquals(1f, CrucibleBasin.fillFraction(CrucibleBasin.RIM_VOLUME));
        assertEquals(CrucibleBasin.RIM_Y, CrucibleBasin.surfaceYForVolume(CrucibleBasin.RIM_VOLUME));
        assertEquals(1f, CrucibleBasin.fillFraction(Integer.MAX_VALUE));
    }
}
