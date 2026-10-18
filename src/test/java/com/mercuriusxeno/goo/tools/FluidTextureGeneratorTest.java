package com.mercuriusxeno.goo.tools;

import org.junit.jupiter.api.Test;
import java.awt.image.BufferedImage;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that FluidTextureGenerator's frame renderer lays a type's accent palette in patches
 * covering the share its coverage names, and lays none for a type without one.
 */
class FluidTextureGeneratorTest {

    private static final int ACCENT_GREEN = 0xFF00FF00;
    private static final float COVERAGE = 0.25f;
    private static final int CELLS = FluidTextureGenerator.SIZE * FluidTextureGenerator.SIZE;
    private static final FluidTextureGenerator.GooFluidGenParams PARAMS =
            new FluidTextureGenerator.GooFluidGenParams(1.2f, 0.05f, 0.01f, 1.5f, 0.02f, 0.7f, 1);

    @Test
    void accentCoversItsShareOfEachFrame() {
        FluidTextureGenerator.Accent green = new FluidTextureGenerator.Accent(
                FluidTextureGenerator.Palette.of(0f, "#00FF00", 1f, "#00FF00"), COVERAGE);
        int accentPixels = countAccentPixels(renderFirstFrame(green));
        assertTrue(accentPixels >= CELLS * COVERAGE / 2 && accentPixels <= CELLS * COVERAGE,
                accentPixels + " of " + CELLS + " cells took the accent at coverage " + COVERAGE);
    }

    @Test
    void typeWithoutAccentPaintsNoAccentPixel() {
        assertEquals(0, countAccentPixels(renderFirstFrame(FluidTextureGenerator.Accent.NONE)));
    }

    private static BufferedImage renderFirstFrame(FluidTextureGenerator.Accent accent) {
        FluidTextureGenerator.GooFluidType type = new FluidTextureGenerator.GooFluidType("test", 40_961L, PARAMS, 2,
                -1f, FluidTextureGenerator.Palette.of(0f, "#202020", 1f, "#C0C0C0"), accent);
        FluidTextureGenerator.FluidCA ca = new FluidTextureGenerator.FluidCA(PARAMS, type.seed());
        FluidTextureGenerator.warmup(ca, FluidTextureGenerator.WARMUP);
        float[][] heatFrames = FluidTextureGenerator.captureHeatFrames(ca);
        float[] heatRange = FluidTextureGenerator.findHeatRange(heatFrames);
        BufferedImage frame = new BufferedImage(FluidTextureGenerator.SIZE, FluidTextureGenerator.SIZE,
                BufferedImage.TYPE_INT_ARGB);
        FluidTextureGenerator.renderFluidFrame(frame, heatFrames[0], 0, heatRange[0], heatRange[1], type,
                AccentPatches.capture(type)[0]);
        return frame;
    }

    private static int countAccentPixels(BufferedImage frame) {
        int count = 0;
        for (int y = 0; y < frame.getHeight(); y++) {
            for (int x = 0; x < frame.getWidth(); x++) {
                count += frame.getRGB(x, y) == ACCENT_GREEN ? 1 : 0;
            }
        }
        return count;
    }
}
