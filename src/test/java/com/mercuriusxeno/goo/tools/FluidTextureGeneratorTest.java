package com.mercuriusxeno.goo.tools;

import org.junit.jupiter.api.Test;
import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that FluidTextureGenerator's frame renderer lays a type's accent palette in patches
 * covering the share its coverage names, in still single-cell specks when its shape asks, and
 * lays none for a type without one.
 */
class FluidTextureGeneratorTest {

    private static final int ACCENT_GREEN = 0xFF00FF00;
    private static final float COVERAGE = 0.25f;
    private static final float SPECK_COVERAGE = 0.03f;
    private static final int CELLS = FluidTextureGenerator.SIZE * FluidTextureGenerator.SIZE;
    private static final FluidTextureGenerator.GooFluidGenParams PARAMS =
            new FluidTextureGenerator.GooFluidGenParams(1.2f, 0.05f, 0.01f, 1.5f, 0.02f, 0.7f, 1);
    private static final FluidTextureGenerator.Palette GREEN = FluidTextureGenerator.Palette.of(
            0f, "#00FF00", 1f, "#00FF00");

    @Test
    void accentCoversItsShareOfEachFrame() {
        FluidTextureGenerator.Accent green = new FluidTextureGenerator.Accent(
                GREEN, COVERAGE, FluidTextureGenerator.AccentShape.PATCHES);
        int accentPixels = accentCells(renderFrame(green, 0)).size();
        assertTrue(accentPixels >= CELLS * COVERAGE / 2 && accentPixels <= CELLS * COVERAGE,
                accentPixels + " of " + CELLS + " cells took the accent at coverage " + COVERAGE);
    }

    @Test
    void typeWithoutAccentPaintsNoAccentPixel() {
        assertEquals(0, accentCells(renderFrame(FluidTextureGenerator.Accent.NONE, 0)).size());
    }

    @Test
    void specksStandStillAcrossFrames() {
        // astral-is-dark-blue-with-starlight-spots
        FluidTextureGenerator.Accent specks = new FluidTextureGenerator.Accent(
                GREEN, SPECK_COVERAGE, FluidTextureGenerator.AccentShape.SPECKS);
        Set<Integer> first = accentCells(renderFrame(specks, 0));
        assertFalse(first.isEmpty(), "no speck in the first frame");
        for (int frame = 1; frame < FluidTextureGenerator.FRAMES; frame++) {
            assertEquals(first, accentCells(renderFrame(specks, frame)), "specks moved by frame " + frame);
        }
    }

    @Test
    void specksAreSingleCellsThatNeverTouch() {
        // astral-is-dark-blue-with-starlight-spots
        FluidTextureGenerator.Accent specks = new FluidTextureGenerator.Accent(
                GREEN, SPECK_COVERAGE, FluidTextureGenerator.AccentShape.SPECKS);
        Set<Integer> cells = accentCells(renderFrame(specks, 0));
        for (int cell : cells) {
            int x = cell % FluidTextureGenerator.SIZE;
            int y = cell / FluidTextureGenerator.SIZE;
            for (int other : cells) {
                int dx = Math.abs(other % FluidTextureGenerator.SIZE - x);
                int dy = Math.abs(other / FluidTextureGenerator.SIZE - y);
                assertTrue(other == cell || Math.max(dx, dy) > 1, "specks at " + cell + " and " + other + " touch");
            }
        }
    }

    private static BufferedImage renderFrame(FluidTextureGenerator.Accent accent, int frameIndex) {
        FluidTextureGenerator.GooFluidType type = new FluidTextureGenerator.GooFluidType("test", 40_961L, PARAMS, 2,
                -1f, FluidTextureGenerator.Palette.of(0f, "#202020", 1f, "#C0C0C0"), accent);
        FluidTextureGenerator.FluidCA ca = new FluidTextureGenerator.FluidCA(PARAMS, type.seed());
        FluidTextureGenerator.warmup(ca, FluidTextureGenerator.WARMUP);
        float[][] heatFrames = FluidTextureGenerator.captureHeatFrames(ca);
        float[] heatRange = FluidTextureGenerator.findHeatRange(heatFrames);
        BufferedImage frame = new BufferedImage(FluidTextureGenerator.SIZE, FluidTextureGenerator.SIZE,
                BufferedImage.TYPE_INT_ARGB);
        FluidTextureGenerator.renderFluidFrame(frame, heatFrames[frameIndex], 0, heatRange[0], heatRange[1], type,
                AccentLayer.capture(type)[frameIndex]);
        return frame;
    }

    private static Set<Integer> accentCells(BufferedImage frame) {
        Set<Integer> cells = new HashSet<>();
        for (int y = 0; y < frame.getHeight(); y++) {
            for (int x = 0; x < frame.getWidth(); x++) {
                if (frame.getRGB(x, y) == ACCENT_GREEN) {
                    cells.add(y * FluidTextureGenerator.SIZE + x);
                }
            }
        }
        return cells;
    }
}
