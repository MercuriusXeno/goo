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
 * its coverage names takes the accent, so the patches drift with the animation. Twinkles: few
 * sporadic stars, each flaring in place at its own strength, opening into a 4-point plus or
 * stopping shy of it, and relighting at another cell once dark.
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
    /**
     * Brightness a flare must pass before its four arms open.
     */
    private static final float ARM_FLOOR = 0.6f;
    /**
     * The weakest peak a flare may draw, which stays shy of its arms.
     */
    private static final float MIN_STRENGTH = 0.35f;
    /**
     * Least reach between any two star cells, so no star's arms ever meet another star.
     */
    private static final int STAR_SPACING = 4;

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
            float[] strengths = new float[cells.length];
            for (int life = 0; life < strengths.length; life++) {
                strengths[life] = MIN_STRENGTH + (1f - MIN_STRENGTH) * random.nextFloat();
            }
            for (int frame = 0; frame < FluidTextureGenerator.FRAMES; frame++) {
                int step = frame + phase;
                float brightness = strengths[step / LIFETIME] * (float) Math.sin(Math.PI * (step % LIFETIME) / LIFETIME);
                lightStar(bands[frame], cells[step / LIFETIME], brightness);
            }
        }
        return bands;
    }

    /**
     * Lights one star in a frame: its center at its brightness, and once the flare climbs past
     * ARM_FLOOR, its four arms, dimmer than the center and brighter the stronger the flare, so a
     * strong flare opens into a 4-point star, a middling one opens partway and a weak one peaks as
     * a lone glint.
     * deep-takes-sculk-and-echo-shard-colors
     *
     * @param frame      the frame's bands
     * @param center     the star's center cell
     * @param brightness the flare's brightness at this frame, 0 to 1
     */
    private static void lightStar(float[] frame, int center, float brightness) {
        if (brightness < LIT_FLOOR) {
            return;
        }
        frame[center] = brightness;
        if (brightness < ARM_FLOOR) {
            return;
        }
        float arm = brightness * (brightness - ARM_FLOOR) / (1f - ARM_FLOOR);
        int size = FluidTextureGenerator.SIZE;
        int x = center % size;
        int y = center / size;
        frame[y * size + Math.floorMod(x + 1, size)] = arm;
        frame[y * size + Math.floorMod(x - 1, size)] = arm;
        frame[Math.floorMod(y + 1, size) * size + x] = arm;
        frame[Math.floorMod(y - 1, size) * size + x] = arm;
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
            if (taken.size() < wanted * LIFETIMES_PER_SLOT
                    && taken.stream().noneMatch(used -> reach(used, cell) < STAR_SPACING)) {
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
     * How many steps, straight or diagonal, part two cells on the frame, counting the wrap a
     * tiling fluid makes at its edges.
     *
     * @param first  one cell's index
     * @param second the other cell's index
     * @return the larger of the wrapped column and row distances
     */
    private static int reach(int first, int second) {
        int size = FluidTextureGenerator.SIZE;
        int dx = Math.abs(first % size - second % size);
        int dy = Math.abs(first / size - second / size);
        return Math.max(Math.min(dx, size - dx), Math.min(dy, size - dy));
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
