package com.mercuriusxeno.goo.client.ability;

import net.minecraft.util.ARGB;
import java.util.Arrays;

/**
 * The reflected color every face of one crystal cloud shows, each easing
 * toward the color its own ray samples a fixed fraction per client tick,
 * so a face whose ray crosses from a dark block to the sky fades across
 * several ticks rather than stepping in one frame.
 */
final class ReflectionEasing {

    /**
     * The share of the gap to the new sample a face's shown color closes
     * each client tick.
     */
    static final double EASE_PER_TICK = 0.3;

    private static final int CHANNELS = 3;
    private static final int GREEN = 1;
    private static final int BLUE = 2;

    private final float[] shownRgb;
    private final boolean[] seeded;
    private double lastClock = Double.NaN;
    private double frameFraction;

    /**
     * An easing for a cloud of at most {@code faceCount} faces.
     *
     * @param faceCount how many faces the cloud can show
     */
    ReflectionEasing(int faceCount) {
        shownRgb = new float[faceCount * CHANNELS];
        seeded = new boolean[faceCount];
    }

    /**
     * Starts a frame at the given clock, fixing the share of the gap every
     * face closes this frame from the ticks elapsed since the last frame.
     * A clock that ran backwards reseeds every face at its sample.
     *
     * @param clock the client tick with its partial tick
     */
    void beginFrame(double clock) {
        double elapsed = Double.isNaN(lastClock) ? 0 : clock - lastClock;
        if (elapsed < 0) {
            Arrays.fill(seeded, false);
            elapsed = 0;
        }
        frameFraction = 1 - Math.pow(1 - EASE_PER_TICK, elapsed);
        lastClock = clock;
    }

    /**
     * The clock of the last frame this cloud drew.
     *
     * @return the clock, or NaN before the first frame
     */
    double lastClock() {
        return lastClock;
    }

    /**
     * Moves one face's shown color toward this frame's sample and answers it.
     * A face seen for the first time shows its sample at once.
     *
     * @param face      the face index within the cloud
     * @param sampleRgb the RGB color the face's ray found this frame
     * @return the RGB color the face shows this frame
     */
    int shownColor(int face, int sampleRgb) {
        int at = face * CHANNELS;
        float[] sample = {ARGB.red(sampleRgb), ARGB.green(sampleRgb), ARGB.blue(sampleRgb)};
        if (!seeded[face]) {
            System.arraycopy(sample, 0, shownRgb, at, CHANNELS);
            seeded[face] = true;
        } else {
            for (int c = 0; c < CHANNELS; c++) {
                shownRgb[at + c] += (float) ((sample[c] - shownRgb[at + c]) * frameFraction);
            }
        }
        return ARGB.color(0, Math.round(shownRgb[at]), Math.round(shownRgb[at + GREEN]),
                Math.round(shownRgb[at + BLUE]));
    }
}
