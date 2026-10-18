package com.mercuriusxeno.goo.tools;

import java.util.Arrays;
import java.util.Comparator;
import java.util.stream.IntStream;

/**
 * Lays a type's accent palette in patches: a second cellular automaton, seeded apart from the
 * one shading the base, ranks each frame's cells, and the share its coverage names takes the
 * accent, so the patches drift with the animation rather than tinting the frame flat.
 */
final class AccentPatches {

    /**
     * Seed offset for the accent CA, decorrelating its patches from the base heat.
     */
    static final long ACCENT_SEED_OFFSET = 104_729L;
    /**
     * Band position of a cell the accent leaves to the base palette.
     */
    static final float UNCOVERED = -1f;

    private AccentPatches() {
    }

    /**
     * Runs the type's accent CA and ranks each frame into its patches.
     *
     * @param type the goo fluid type
     * @return per frame, per cell, the cell's position in its patch band, or UNCOVERED
     */
    static float[][] capture(FluidTextureGenerator.GooFluidType type) {
        FluidTextureGenerator.FluidCA ca = new FluidTextureGenerator.FluidCA(
                type.genParams(), type.seed() + ACCENT_SEED_OFFSET);
        FluidTextureGenerator.warmup(ca, FluidTextureGenerator.WARMUP);
        float[][] field = FluidTextureGenerator.captureHeatFrames(ca);
        float[][] bands = new float[field.length][];
        for (int frame = 0; frame < field.length; frame++) {
            bands[frame] = selectPatches(field[frame], type.accent().coverage());
        }
        return bands;
    }

    /**
     * Picks the hottest share of a frame's cells, the hottest at band position 1 and the coolest
     * picked at the rim, near 0; every other cell answers UNCOVERED.
     *
     * @param field    one frame of the accent CA's heat
     * @param coverage the share of cells to pick
     * @return each cell's band position, or UNCOVERED
     */
    static float[] selectPatches(float[] field, float coverage) {
        float[] bands = new float[field.length];
        Arrays.fill(bands, UNCOVERED);
        int picked = Math.round(coverage * field.length);
        Integer[] hottestFirst = IntStream.range(0, field.length).boxed()
                .sorted(Comparator.comparingDouble((Integer cell) -> field[cell]).reversed())
                .toArray(Integer[]::new);
        for (int rank = 0; rank < picked; rank++) {
            bands[hottestFirst[rank]] = 1f - (float) rank / picked;
        }
        return bands;
    }

    /**
     * The color a cell takes: the accent where a patch covers it, the base palette elsewhere.
     *
     * @param type           the goo fluid type
     * @param normalizedHeat the cell's base heat, 0 to 1
     * @param band           the cell's patch band position, or UNCOVERED
     * @return the ARGB color
     */
    static int sampleColor(FluidTextureGenerator.GooFluidType type, float normalizedHeat, float band) {
        if (band == UNCOVERED) {
            return type.palette().sample(normalizedHeat);
        }
        return type.accent().palette().sample(band);
    }
}
