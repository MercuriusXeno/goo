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
 * its coverage names takes the accent, so the patches drift with the animation. Twinkles: rare
 * single-cell stars, each fading in and out at one cell and relighting at another once dark.
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
     * Frames one star lifetime runs, from dark through its brightest back to dark.
     */
    static final int LIFETIME = 16;
    /**
     * Lifetimes a slot can start within one loop of frames, its phase pushing the last past the end.
     */
    private static final int LIFETIMES_PER_SLOT = FluidTextureGenerator.FRAMES / LIFETIME + 1;
    /**
     * Brightness below which a star counts as dark and leaves its cell to the base palette.
     */
    private static final float LIT_FLOOR = 0.2f;

    private AccentLayer() {
    }

    /**
     * Lays the type's accent in the shape it names.
     *
     * @param type the goo fluid type
     * @return per frame, per cell, the cell's position in the accent band, or UNCOVERED
     */
    static float[][] capture(FluidTextureGenerator.GooFluidType type) {
        if (type.accent().shape() == FluidTextureGenerator.AccentShape.TWINKLES) {
            return twinkleStars(type);
        }
        return rankPatches(type);
    }

    /**
     * Lights the type's stars: each star slot runs lifetimes of LIFETIME frames on its own phase,
     * and in each lifetime fades in, shines and fades out at one cell, then relights at the next
     * cell once dark. Every cell any star uses stands clear of every other, so no lit cell sits
     * beside another lit cell in the same frame or the next one, where interpolation would smear
     * it into a streak.
     * astral-is-dark-blue-with-starlight-spots
     * deep-takes-sculk-and-echo-shard-colors
     *
     * @param type the goo fluid type
     * @return per frame, per cell, the star's brightness from rim (0) to heart (1), or UNCOVERED
     */
    static float[][] twinkleStars(FluidTextureGenerator.GooFluidType type) {
        int cellCount = FluidTextureGenerator.SIZE * FluidTextureGenerator.SIZE;
        Random random = new Random(type.seed() + ACCENT_SEED_OFFSET);
        List<int[]> slots = placeStarSlots(random, (int) (type.accent().coverage() * cellCount));
        float[][] bands = new float[FluidTextureGenerator.FRAMES][cellCount];
        for (float[] frame : bands) {
            Arrays.fill(frame, UNCOVERED);
        }
        for (int[] cells : slots) {
            int phase = random.nextInt(LIFETIME);
            for (int frame = 0; frame < FluidTextureGenerator.FRAMES; frame++) {
                int step = frame + phase;
                float brightness = (float) Math.sin(Math.PI * (step % LIFETIME) / LIFETIME);
                if (brightness >= LIT_FLOOR) {
                    bands[frame][cells[step / LIFETIME]] = brightness;
                }
            }
        }
        return bands;
    }

    /**
     * Picks each star slot's cells, one per lifetime it lives through, every cell clear of every
     * other cell picked; slots stop where the frame runs out of clear cells.
     *
     * @param random the type's seeded generator
     * @param wanted how many star slots the coverage asks for
     * @return each slot's cells in lifetime order
     */
    private static List<int[]> placeStarSlots(Random random, int wanted) {
        int cellCount = FluidTextureGenerator.SIZE * FluidTextureGenerator.SIZE;
        List<Integer> candidates = new ArrayList<>(IntStream.range(0, cellCount).boxed().toList());
        Collections.shuffle(candidates, random);
        List<Integer> taken = new ArrayList<>();
        for (int cell : candidates) {
            if (taken.size() < wanted * LIFETIMES_PER_SLOT && taken.stream().noneMatch(used -> touching(used, cell))) {
                taken.add(cell);
            }
        }
        List<int[]> slots = new ArrayList<>();
        for (int start = 0; start + LIFETIMES_PER_SLOT <= taken.size(); start += LIFETIMES_PER_SLOT) {
            slots.add(taken.subList(start, start + LIFETIMES_PER_SLOT).stream().mapToInt(Integer::intValue).toArray());
        }
        return slots;
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
