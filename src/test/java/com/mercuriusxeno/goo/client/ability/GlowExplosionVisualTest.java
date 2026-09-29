package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.client.GooRenderTypes;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * GlowExplosionVisual's timing, and the shader pair its pipeline names.
 */
class GlowExplosionVisualTest {

    private static final float TOLERANCE = 1e-5f;

    @Test
    void domeRisesToItsReachFastThenSlow() {
        assertEquals(0f, GlowExplosionVisual.domeRadius(0f), 0f);
        assertEquals(GlowExplosionVisual.DOME_REACH, GlowExplosionVisual.domeRadius(1f), TOLERANCE);
        float early = GlowExplosionVisual.domeRadius(0.25f);
        float late = GlowExplosionVisual.domeRadius(1f) - GlowExplosionVisual.domeRadius(0.75f);
        assertTrue(early > late, "the dome does not ease out");
    }

    @Test
    void bloomBreathesBrighterOnceThenFades() {
        float peak = GlowExplosionVisual.BREATH_PEAK;
        assertEquals(0f, GlowExplosionVisual.brightness(0f), 0f);
        assertTrue(GlowExplosionVisual.brightness(peak / 2) < 1f, "the bloom starts at full");
        assertEquals(1f, GlowExplosionVisual.brightness(peak), TOLERANCE);
        assertEquals(0.5f, GlowExplosionVisual.brightness((1f + peak) / 2), TOLERANCE);
        assertEquals(0f, GlowExplosionVisual.brightness(1f), TOLERANCE);
    }

    @Test
    void pipelineShadersResolveOnTheClasspath() {
        PipelineShaders.assertExist(GooRenderTypes.GLOW_EXPLOSION);
    }
}
