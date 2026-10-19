package com.mercuriusxeno.goo.tools;

import org.junit.jupiter.api.Test;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that FluidTextureGenerator's frame renderer lays a type's accent palette in patches
 * covering the share its coverage names, in few sporadic stars that flare in place, some opening
 * into 4-point pluses and some stopping shy, when its shape asks, and lays none for a type
 * without one.
 */
class FluidTextureGeneratorTest {

    private static final int ACCENT_GREEN = 0xFF00FF00;
    private static final float COVERAGE = 0.25f;
    private static final float STAR_COVERAGE = 0.03f;
    private static final int PLUS_CELLS = 5;
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
    void everyLitStarIsAPlusAroundItsBrightestCell() {
        // deep-takes-sculk-and-echo-shard-colors: the echo shard's 4-point glint
        float[][] bands = starBands();
        for (int frame = 0; frame < FluidTextureGenerator.FRAMES; frame++) {
            for (List<Integer> star : stars(bands[frame])) {
                int center = brightest(star, bands[frame]);
                assertTrue(star.size() <= PLUS_CELLS, "a star of " + star.size() + " cells in frame " + frame);
                for (int cell : star) {
                    assertTrue(cell == center || orthogonalNeighbors(center).contains(cell),
                            "cell " + cell + " sits off the plus around " + center + " in frame " + frame);
                }
            }
        }
    }

    @Test
    void aStarNeverSlidesBetweenFrames() {
        // astral-is-dark-blue-with-starlight-spots: interpolation smears a slide into a streak
        float[][] bands = starBands();
        for (int frame = 1; frame < FluidTextureGenerator.FRAMES; frame++) {
            Set<Integer> centersBefore = centers(bands[frame - 1]);
            for (int center : centers(bands[frame])) {
                if (!centersBefore.contains(center)) {
                    for (int cell = 0; cell < CELLS; cell++) {
                        assertTrue(bands[frame - 1][cell] == AccentLayer.UNCOVERED || reach(center, cell) > 2,
                                "the star at " + center + " lit beside " + cell + " lit a frame before " + frame);
                    }
                }
            }
        }
    }

    @Test
    void starsStayFew() {
        // astral-is-dark-blue-with-starlight-spots
        float[][] bands = starBands();
        int lit = 0;
        for (int frame = 0; frame < FluidTextureGenerator.FRAMES; frame++) {
            int count = stars(bands[frame]).size();
            lit += count;
            assertTrue(count <= (int) (CELLS * STAR_COVERAGE), count + " stars lit in frame " + frame);
        }
        assertTrue(lit > 0, "no star lit in any frame");
    }

    @Test
    void someFlaresOpenIntoFourPointsAndSomeStopShy() {
        // deep-takes-sculk-and-echo-shard-colors: sporadic, and sometimes shy of 4 points
        float[][] bands = starBands();
        boolean fullStar = false;
        boolean shyPeak = false;
        for (int frame = 1; frame < FluidTextureGenerator.FRAMES - 1; frame++) {
            for (List<Integer> star : stars(bands[frame])) {
                fullStar |= star.size() == PLUS_CELLS;
                int center = brightest(star, bands[frame]);
                shyPeak |= star.size() == 1 && bands[frame][center] >= bands[frame - 1][center]
                        && bands[frame][center] >= bands[frame + 1][center];
            }
        }
        assertTrue(fullStar, "no flare opened into a full 4-point star");
        assertTrue(shyPeak, "no flare peaked as a lone glint");
    }

    private static float[][] starBands() {
        FluidTextureGenerator.Accent accent = new FluidTextureGenerator.Accent(
                GREEN, STAR_COVERAGE, FluidTextureGenerator.AccentShape.TWINKLES);
        return AccentLayer.capture(new FluidTextureGenerator.GooFluidType("test", 40_961L, PARAMS, 2, -1f,
                FluidTextureGenerator.Palette.of(0f, "#202020", 1f, "#C0C0C0"), accent));
    }

    private static List<List<Integer>> stars(float[] frame) {
        List<List<Integer>> stars = new ArrayList<>();
        Set<Integer> seen = new HashSet<>();
        for (int start = 0; start < CELLS; start++) {
            if (frame[start] == AccentLayer.UNCOVERED || !seen.add(start)) {
                continue;
            }
            List<Integer> star = new ArrayList<>(List.of(start));
            for (int index = 0; index < star.size(); index++) {
                for (int cell = 0; cell < CELLS; cell++) {
                    if (frame[cell] != AccentLayer.UNCOVERED && reach(star.get(index), cell) == 1 && seen.add(cell)) {
                        star.add(cell);
                    }
                }
            }
            stars.add(star);
        }
        return stars;
    }

    private static Set<Integer> centers(float[] frame) {
        Set<Integer> centers = new HashSet<>();
        for (List<Integer> star : stars(frame)) {
            centers.add(brightest(star, frame));
        }
        return centers;
    }

    private static int brightest(List<Integer> star, float[] frame) {
        return star.stream().max((first, second) -> Float.compare(frame[first], frame[second])).orElseThrow();
    }

    private static Set<Integer> orthogonalNeighbors(int cell) {
        Set<Integer> neighbors = new HashSet<>();
        for (int other = 0; other < CELLS; other++) {
            int dx = wrapped(other % FluidTextureGenerator.SIZE - cell % FluidTextureGenerator.SIZE);
            int dy = wrapped(other / FluidTextureGenerator.SIZE - cell / FluidTextureGenerator.SIZE);
            if (dx + dy == 1) {
                neighbors.add(other);
            }
        }
        return neighbors;
    }

    private static int reach(int first, int second) {
        int dx = wrapped(first % FluidTextureGenerator.SIZE - second % FluidTextureGenerator.SIZE);
        int dy = wrapped(first / FluidTextureGenerator.SIZE - second / FluidTextureGenerator.SIZE);
        return Math.max(dx, dy);
    }

    private static int wrapped(int offset) {
        int distance = Math.abs(offset);
        return Math.min(distance, FluidTextureGenerator.SIZE - distance);
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
