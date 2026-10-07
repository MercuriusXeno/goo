package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.client.GooRenderTypes;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RockExplosionVisual's timing, and the shader pair its pipeline names.
 */
class RockExplosionVisualTest {

    private static final float TOLERANCE = 1e-5f;
    private static final int SAMPLES = 28;

    @Test
    void discSpreadsFromNothingToItsReachFastThenSlow() {
        assertEquals(0f, RockExplosionVisual.discRadius(0f), 0f);
        assertEquals(RockExplosionVisual.DISC_REACH, RockExplosionVisual.discRadius(1f), TOLERANCE);
        float previous = 0f;
        for (int i = 1; i <= SAMPLES; i++) {
            float radius = RockExplosionVisual.discRadius(i / (float) SAMPLES);
            assertTrue(radius >= previous, "the disc shrinks at sample " + i);
            previous = radius;
        }
        float early = RockExplosionVisual.discRadius(0.25f);
        float late = RockExplosionVisual.discRadius(1f) - RockExplosionVisual.discRadius(0.75f);
        assertTrue(early > late, "the disc does not ease out");
    }

    @Test
    void sonicRingCrossesTheDiscOnceOverTheFirstSpan() {
        assertEquals(0f, RockExplosionVisual.sonicRingRadial(0f), 0f);
        assertEquals(0.5f, RockExplosionVisual.sonicRingRadial(RockExplosionVisual.SONIC_SPAN / 2), TOLERANCE);
        assertEquals(1f, RockExplosionVisual.sonicRingRadial(RockExplosionVisual.SONIC_SPAN), TOLERANCE);
        assertEquals(1f, RockExplosionVisual.sonicRingRadial(1f), 0f);
    }

    @Test
    void cursorLoopsTheOpeningShareOfTheExplosion() {
        float half = RockExplosionVisual.DURATION_TICKS / 2f;
        assertEquals(0f, RockExplosionVisual.cursorProgress(0f), 0f);
        assertEquals(RockExplosionVisual.CURSOR_SPAN / 2, RockExplosionVisual.cursorProgress(half), TOLERANCE);
        assertEquals(0f, RockExplosionVisual.cursorProgress(RockExplosionVisual.DURATION_TICKS), TOLERANCE);
        assertEquals(RockExplosionVisual.cursorProgress(half),
                RockExplosionVisual.cursorProgress(RockExplosionVisual.DURATION_TICKS + half), TOLERANCE);
    }

    @Test
    void pipelineShadersResolveOnTheClasspath() {
        PipelineShaders.assertExist(GooRenderTypes.ROCK_EXPLOSION);
    }
}
