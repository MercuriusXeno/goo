package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.client.GooRenderTypes;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FrostExplosionVisual's timing, and the shader pair its pipeline names.
 */
class FrostExplosionVisualTest {

    private static final float TOLERANCE = 1e-5f;
    private static final int SAMPLES = 32;

    @Test
    void novaExpandsFromNothingToItsReachFastThenSlow() {
        assertEquals(0f, FrostExplosionVisual.novaRadius(0f), 0f);
        assertEquals(FrostExplosionVisual.NOVA_REACH, FrostExplosionVisual.novaRadius(1f), TOLERANCE);
        float previous = 0f;
        for (int i = 1; i <= SAMPLES; i++) {
            float radius = FrostExplosionVisual.novaRadius(i / (float) SAMPLES);
            assertTrue(radius >= previous, "the nova shrinks at sample " + i);
            previous = radius;
        }
        float early = FrostExplosionVisual.novaRadius(0.25f);
        float late = FrostExplosionVisual.novaRadius(1f) - FrostExplosionVisual.novaRadius(0.75f);
        assertTrue(early > late, "the nova does not ease out");
    }

    @Test
    void frostCrystallizesOverItsSpanThenStaysCrisp() {
        assertEquals(0f, FrostExplosionVisual.crystallized(0f), 0f);
        assertEquals(0.5f, FrostExplosionVisual.crystallized(FrostExplosionVisual.CRYSTALLIZE_SPAN / 2), TOLERANCE);
        assertEquals(1f, FrostExplosionVisual.crystallized(FrostExplosionVisual.CRYSTALLIZE_SPAN), TOLERANCE);
        assertEquals(1f, FrostExplosionVisual.crystallized(1f), 0f);
    }

    @Test
    void novaHoldsABeatThenFades() {
        assertEquals(1f, FrostExplosionVisual.remaining(0f), 0f);
        assertEquals(1f, FrostExplosionVisual.remaining(FrostExplosionVisual.FADE_START), 0f);
        float midFade = (1f + FrostExplosionVisual.FADE_START) / 2;
        assertEquals(0.5f, FrostExplosionVisual.remaining(midFade), TOLERANCE);
        assertEquals(0f, FrostExplosionVisual.remaining(1f), TOLERANCE);
    }

    @Test
    void pipelineShadersResolveOnTheClasspath() {
        PipelineShaders.assertExist(GooRenderTypes.FROST_EXPLOSION);
    }
}
