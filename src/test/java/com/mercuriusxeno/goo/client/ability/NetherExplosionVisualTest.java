package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.client.GooRenderTypes;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * NetherExplosionVisual's timing, and the shader pair its pipeline names.
 */
class NetherExplosionVisualTest {

    private static final float TOLERANCE = 1e-5f;
    private static final int SAMPLES = 30;

    @Test
    void rushFallsFromItsStartOntoTheMarkerSlowThenFast() {
        assertEquals(NetherExplosionVisual.RUSH_START, NetherExplosionVisual.rushRadius(0f), 0f);
        assertEquals(0f, NetherExplosionVisual.rushRadius(1f), TOLERANCE);
        float previous = NetherExplosionVisual.RUSH_START;
        for (int i = 1; i <= SAMPLES; i++) {
            float radius = NetherExplosionVisual.rushRadius(i / (float) SAMPLES);
            assertTrue(radius <= previous, "the rush widens at sample " + i);
            previous = radius;
        }
        float early = NetherExplosionVisual.RUSH_START - NetherExplosionVisual.rushRadius(0.25f);
        float late = NetherExplosionVisual.rushRadius(0.75f) - NetherExplosionVisual.rushRadius(1f);
        assertTrue(late > early, "the rush does not speed up as it falls in");
    }

    @Test
    void rushHoldsThenFadesUnderTheHole() {
        assertEquals(1f, NetherExplosionVisual.remaining(0f), 0f);
        assertEquals(1f, NetherExplosionVisual.remaining(NetherExplosionVisual.FADE_START), 0f);
        float midFade = (1f + NetherExplosionVisual.FADE_START) / 2;
        assertEquals(0.5f, NetherExplosionVisual.remaining(midFade), TOLERANCE);
        assertEquals(0f, NetherExplosionVisual.remaining(1f), TOLERANCE);
    }

    @Test
    void pipelineShadersResolveOnTheClasspath() {
        PipelineShaders.assertExist(GooRenderTypes.NETHER_EXPLOSION);
    }
}
