package com.mercuriusxeno.goo.tools;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;

/**
 * Lays a type's accent palette over its frames in one of two shapes. Patches: a second cellular
 * automaton, seeded apart from the one shading the base, ranks each frame's cells, and the share
 * its coverage names takes the accent, so the patches drift with the animation. Specks: rare
 * single cells, scattered once and standing still, apart from each other, each twinkling on its
 * own phase between the accent's dim rim and its bright heart.
 */
final class AccentLayer {

    /**
     * Seed offset for the accent CA, decorrelating its patches from the base heat.
     */
    static final long ACCENT_SEED_OFFSET = 104_729L;
    /**
     * Band position of a cell the accent leaves to the base palette.
     */
    static final float UNCOVERED = -1f;
    /**
     * Midpoint and half-swing of a speck's twinkle across the accent palette.
     */
    private static final float TWINKLE_MID = 0.5f;

    private AccentLayer() {
    }

    /**
     * Lays the type's accent in the shape it names.
     *
     * @param type the goo fluid type
     * @return per frame, per cell, the cell's position in the accent band, or UNCOVERED
     */
    static float[][] capture(FluidTextureGenerator.GooFluidType type) {
        if (type.accent().shape() == FluidTextureGenerator.AccentShape.SPECKS) {
            return scatterSpecks(type);
        }
        return rankPatches(type);
    }

    /**
     * Scatters the type's specks once, each at least one cell clear of every other on the tiling
     * frame, and twinkles each in place across the frames.
     * astral-is-dark-blue-with-starlight-spots
     * deep-takes-sculk-and-echo-shard-colors
     *
     * @param type the goo fluid type
     * @return per frame, per cell, the speck's twinkle position, or UNCOVERED
     */
    static float[][] scatterSpecks(FluidTextureGenerator.GooFluidType type) {
        int cellCount = FluidTextureGenerator.SIZE * FluidTextureGenerator.SIZE;
        Random random = new Random(type.seed() + ACCENT_SEED_OFFSET);
        List<Integer> candidates = new ArrayList<>(IntStream.range(0, cellCount).boxed().toList());
        Collections.shuffle(candidates, random);
        int wanted = Math.round(type.accent().coverage() * cellCount);
        List<Integer> specks = new ArrayList<>();
        for (int cell : candidates) {
            if (specks.size() < wanted && specks.stream().noneMatch(speck -> touching(speck, cell))) {
                specks.add(cell);
            }
        }
        float[][] bands = new float[FluidTextureGenerator.FRAMES][cellCount];
        for (float[] frame : bands) {
            Arrays.fill(frame, UNCOVERED);
        }
        for (int speck : specks) {
            float phase = random.nextFloat();
            for (int frame = 0; frame < FluidTextureGenerator.FRAMES; frame++) {
                double turn = (double) frame / FluidTextureGenerator.FRAMES + phase;
                bands[frame][speck] = (float) (TWINKLE_MID + TWINKLE_MID * Math.sin(Math.TAU * turn));
            }
        }
        return bands;
    }

    /**
     * Whether two cells sit side by side or corner to corner on the frame, counting the wrap a
     * tiling fluid makes at its edges.
     *
     * @param first  one cell's index
     * @param second the other cell's index
     * @return true when the cells touch
     */
    private static boolean touching(int first, int second) {
        int size = FluidTextureGenerator.SIZE;
        int dx = Math.abs(first % size - second % size);
        int dy = Math.abs(first / size - second / size);
        return Math.min(dx, size - dx) <= 1 && Math.min(dy, size - dy) <= 1;
    }

    /**
     * Runs the type's accent CA and ranks each frame into its patches.
     *
     * @param type the goo fluid type
     * @return per frame, per cell, the cell's position in its patch band, or UNCOVERED
     */
    private static float[][] rankPatches(FluidTextureGenerator.GooFluidType type) {
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
