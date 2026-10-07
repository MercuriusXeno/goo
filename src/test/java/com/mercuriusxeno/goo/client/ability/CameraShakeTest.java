package com.mercuriusxeno.goo.client.ability;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The bore's camera shake stays within its amplitude on every axis and
 * keeps moving, so the world trembles without swinging
 * (decision bore-vortex-with-a-worldspace-shake).
 */
class CameraShakeTest {

    private static final int SAMPLES = 400;
    private static final float SAMPLE_STEP = 0.1f;
    /** The least a sampled axis must swing for the shake to read as a tremble. */
    private static final float VISIBLE_SWING = 0.3f;

    @Test
    void everyAxisStaysWithinTheAmplitudeAndSwingsVisibly() {
        float[] min = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE};
        float[] max = {-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        for (int i = 0; i < SAMPLES; i++) {
            CameraShake.Offset offset = CameraShake.offsetAt(i * SAMPLE_STEP);
            float[] axes = {offset.pitch(), offset.yaw(), offset.roll()};
            for (int axis = 0; axis < axes.length; axis++) {
                assertTrue(Math.abs(axes[axis]) <= CameraShake.AMPLITUDE_DEGREES, "axis " + axis + " at " + i);
                min[axis] = Math.min(min[axis], axes[axis]);
                max[axis] = Math.max(max[axis], axes[axis]);
            }
        }
        for (int axis = 0; axis < min.length; axis++) {
            assertTrue(max[axis] - min[axis] > VISIBLE_SWING, "axis " + axis + " barely moves");
        }
    }
}
