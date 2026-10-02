package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.client.GooRenderTypes;
import net.minecraft.util.ARGB;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UnstableExplosionVisual's timing, and the shader pair its pipeline names.
 */
class UnstableExplosionVisualTest {

    private static final float REACH = 4f;
    private static final float TOLERANCE = 1e-5f;
    private static final int SAMPLES = 32;

    @Test
    void fireballGrowsFromNothingToTheBlastRadius() {
        assertEquals(0f, UnstableExplosionVisual.sphereRadius(0f, REACH), 0f);
        assertEquals(REACH, UnstableExplosionVisual.sphereRadius(1f, REACH), TOLERANCE);
        float previous = 0f;
        for (int i = 1; i <= SAMPLES; i++) {
            float radius = UnstableExplosionVisual.sphereRadius(i / (float) SAMPLES, REACH);
            assertTrue(radius >= previous, "the fireball shrinks at sample " + i);
            previous = radius;
        }
    }

    @Test
    void fireballGrowsFastThenSlow() {
        float early = UnstableExplosionVisual.sphereRadius(0.25f, REACH);
        float late = UnstableExplosionVisual.sphereRadius(1f, REACH) - UnstableExplosionVisual.sphereRadius(0.75f, REACH);
        assertTrue(early > late, "the fireball does not ease out");
    }

    @Test
    void ringRunsAheadOfTheFireball() {
        for (int i = 1; i <= SAMPLES; i++) {
            float progress = i / (float) SAMPLES;
            assertTrue(UnstableExplosionVisual.ringRadius(progress, REACH)
                            > UnstableExplosionVisual.sphereRadius(progress, REACH),
                    "the ring falls behind at progress " + progress);
        }
        assertEquals(REACH * UnstableExplosionVisual.RING_REACH,
                UnstableExplosionVisual.ringRadius(1f, REACH), TOLERANCE);
    }

    @Test
    void rampMeetsTheFireballsFirstDrawnFrame() {
        float firstTick = 1f / UnstableExplosionVisual.DURATION_TICKS;
        DomeRampShape.assertRampMeetsFirstFrame(ramp -> UnstableExplosionVisual.rampSphereRadius(ramp, REACH),
                UnstableExplosionVisual.sphereRadius(firstTick, REACH),
                ramp -> ARGB.alpha(UnstableExplosionVisual.sphereColor(UnstableExplosionVisual.FIRST_DRAWN_PROGRESS,
                        DomeRamp.alpha(ramp))));
        DomeRampShape.assertRampMeetsFirstFrame(ramp -> UnstableExplosionVisual.rampRingRadius(ramp, REACH),
                UnstableExplosionVisual.ringRadius(firstTick, REACH),
                ramp -> ARGB.alpha(UnstableExplosionVisual.ringColor(UnstableExplosionVisual.FIRST_DRAWN_PROGRESS,
                        true, DomeRamp.alpha(ramp))));
    }

    @Test
    void pipelineShadersResolveOnTheClasspath() {
        PipelineShaders.assertExist(GooRenderTypes.UNSTABLE_EXPLOSION);
    }
}
