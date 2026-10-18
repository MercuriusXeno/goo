package com.mercuriusxeno.goo.tools;

import org.junit.jupiter.api.Test;
import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that FluidTextureGenerator's frame renderer lays a type's accent palette in patches
 * covering the share its coverage names, in rare single-cell stars that twinkle in place and
 * relight elsewhere when its shape asks, and lays none for a type without one.
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
    void twinklesAreRareSingleCellsThatNeverTouch() {
        // astral-is-dark-blue-with-starlight-spots
        for (int frame = 0; frame < FluidTextureGenerator.FRAMES; frame++) {
            Set<Integer> cells = accentCells(renderFrame(twinkles(), frame));
            assertTrue(cells.size() <= CELLS * SPECK_COVERAGE,
                    cells.size() + " stars lit in frame " + frame + ", too many for coverage " + SPECK_COVERAGE);
            for (int cell : cells) {
                for (int other : cells) {
                    assertTrue(other == cell || !touching(cell, other),
                            "stars at " + cell + " and " + other + " touch in frame " + frame);
                }
            }
        }
    }

    @Test
    void aLitStarNeverSlidesToANeighborBetweenFrames() {
        // astral-is-dark-blue-with-starlight-spots: interpolation smears a slide into a streak
        Set<Integer> previous = accentCells(renderFrame(twinkles(), 0));
        for (int frame = 1; frame < FluidTextureGenerator.FRAMES; frame++) {
            Set<Integer> current = accentCells(renderFrame(twinkles(), frame));
            for (int cell : current) {
                for (int before : previous) {
                    assertTrue(!touching(cell, before),
                            "a star slid from " + before + " to " + cell + " into frame " + frame);
                }
            }
            previous = current;
        }
    }

    @Test
    void starsRelightAtNewCells() {
        // astral-is-dark-blue-with-starlight-spots
        Set<Integer> everLit = new HashSet<>();
        int mostAtOnce = 0;
        for (int frame = 0; frame < FluidTextureGenerator.FRAMES; frame++) {
            Set<Integer> cells = accentCells(renderFrame(twinkles(), frame));
            everLit.addAll(cells);
            mostAtOnce = Math.max(mostAtOnce, cells.size());
        }
        assertTrue(mostAtOnce > 0, "no star lit in any frame");
        assertTrue(everLit.size() > mostAtOnce, "the stars never moved to new cells: " + everLit);
    }

    private static boolean touching(int first, int second) {
        int dx = Math.abs(first % FluidTextureGenerator.SIZE - second % FluidTextureGenerator.SIZE);
        int dy = Math.abs(first / FluidTextureGenerator.SIZE - second / FluidTextureGenerator.SIZE);
        return Math.max(dx, dy) == 1;
    }

    private static FluidTextureGenerator.Accent twinkles() {
        return new FluidTextureGenerator.Accent(GREEN, SPECK_COVERAGE, FluidTextureGenerator.AccentShape.TWINKLES);
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
