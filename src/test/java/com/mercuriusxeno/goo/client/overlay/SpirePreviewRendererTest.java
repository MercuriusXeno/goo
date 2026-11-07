package com.mercuriusxeno.goo.client.overlay;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Spire's ripple reads as one smooth sheet, not a checkerboard: each quad
 * takes the wave's color at its corners, so neighbouring quads share the
 * colors along their shared edge and the sheet blends across them
 * (decision spire-previews-as-a-surface-ripple-and-ghost-stone).
 */
class SpirePreviewRendererTest {

    private static final float DAY = 0.37f;
    private static final int GOO_RGB = 0x8A7F70;
    private static final double STEP = 1.0 / SpirePreviewRenderer.RIPPLE_QUADS_ACROSS;

    @Test
    void neighbouringQuadsShareTheColorsAlongTheirSharedEdge() {
        int[] west = SpirePreviewRenderer.quadColors(10, 20, STEP, DAY, GOO_RGB);
        int[] east = SpirePreviewRenderer.quadColors(10 + STEP, 20, STEP, DAY, GOO_RGB);
        assertEquals(west[SpirePreviewRenderer.NORTH_EAST], east[SpirePreviewRenderer.NORTH_WEST], "the shared edge's north corner differs");
        assertEquals(west[SpirePreviewRenderer.SOUTH_EAST], east[SpirePreviewRenderer.SOUTH_WEST], "the shared edge's south corner differs");
    }

    @Test
    void aQuadsCornersRideTheWaveRatherThanOneFlatColor() {
        int[] corners = SpirePreviewRenderer.quadColors(10, 20, STEP, DAY, GOO_RGB);
        boolean varies = corners[0] != corners[1] || corners[0] != corners[2] || corners[0] != corners[3];
        assertTrue(varies, "every corner of the quad took one color");
    }

    @Test
    void theGridSamplesTheWaveAtLeastFiveTimesAWavelength() {
        double wavelength = 2 * Math.PI / com.mercuriusxeno.goo.client.SurfaceRipple.SECONDARY_WAVENUMBER;
        assertTrue(wavelength / STEP >= 5, "the grid samples the wave " + wavelength / STEP + " times a wavelength");
    }
}
