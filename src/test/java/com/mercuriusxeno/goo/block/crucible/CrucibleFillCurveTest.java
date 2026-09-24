package com.mercuriusxeno.goo.block.crucible;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the crucible's shared fill curve: the first ticks of a melt stay
 * under the first pixel, each pixel takes more volume than the one below it,
 * and the level keeps moving until the rim volume.
 */
class CrucibleFillCurveTest {

    private static final float ONE_PIXEL = 1f / CrucibleBasin.PIXEL_COUNT;
    /** A full stack of storage blocks of the densest goo value melted: 64 x 9 x 13824 mB. */
    private static final int DENSE_STACK_VOLUME = 64 * 9 * 13_824;
    /** A volume far past that stack, twelve stacks over. */
    private static final int FAR_PAST_A_DENSE_STACK = DENSE_STACK_VOLUME * 12;

    @Test
    void emptyCrucibleHasNoFill() {
        assertEquals(0f, CrucibleBasin.fillFraction(0));
        assertEquals(0f, CrucibleBasin.fillFraction(-100));
    }

    @Test
    void firstTicksOfAMeltStayUnderTheFirstPixel() {
        for (int volume = 50; volume <= 200; volume += 50) {
            float fraction = CrucibleBasin.fillFraction(volume);
            assertTrue(fraction > 0f && fraction < ONE_PIXEL,
                volume + " mB answered " + fraction + ", not inside the first pixel");
        }
    }

    @Test
    void firstPixelVolumeRaisesTheSurfaceOnePixel() {
        assertEquals(ONE_PIXEL, CrucibleBasin.fillFraction(CrucibleBasin.FIRST_PIXEL_VOLUME), 1e-4f);
    }

    @Test
    void eachPixelTakesMoreVolumeThanTheOneBelow() {
        double previousGap = 0;
        for (int pixel = 1; pixel <= CrucibleBasin.PIXEL_COUNT; pixel++) {
            double gap = CrucibleBasin.pixelCrossingVolume(pixel) - CrucibleBasin.pixelCrossingVolume(pixel - 1);
            assertTrue(gap > previousGap, "pixel " + pixel + " took " + gap + " mB, not more than " + previousGap);
            previousGap = gap;
        }
    }

    @Test
    void pixelCrossingsLandOnThePixelLines() {
        for (int pixel = 1; pixel < CrucibleBasin.PIXEL_COUNT; pixel++) {
            int volume = (int) Math.round(CrucibleBasin.pixelCrossingVolume(pixel));
            assertEquals(pixel * ONE_PIXEL, CrucibleBasin.fillFraction(volume), 1e-4f, "pixel " + pixel);
        }
    }

    @Test
    void pixelCrossingsSpanFloorToRimVolume() {
        assertEquals(0.0, CrucibleBasin.pixelCrossingVolume(0), 1e-9);
        assertEquals(CrucibleBasin.FIRST_PIXEL_VOLUME, CrucibleBasin.pixelCrossingVolume(1), 1e-3);
        assertEquals(CrucibleBasin.RIM_VOLUME, CrucibleBasin.pixelCrossingVolume(CrucibleBasin.PIXEL_COUNT), 1.0);
    }

    @Test
    void fillRisesStrictlyOverAGeometricSweep() {
        float previous = 0f;
        for (long volume = 1; volume < CrucibleBasin.RIM_VOLUME; volume = volume * 3 / 2 + 1) {
            float fraction = CrucibleBasin.fillFraction((int) volume);
            assertTrue(fraction > previous, "fill held at " + fraction + " by " + volume + " mB");
            previous = fraction;
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
        assertTrue(CrucibleBasin.fillFraction(CrucibleBasin.RIM_VOLUME - 1) < 1f);
        assertEquals(1f, CrucibleBasin.fillFraction(CrucibleBasin.RIM_VOLUME));
        assertEquals(1f, CrucibleBasin.fillFraction(Integer.MAX_VALUE));
    }
}
