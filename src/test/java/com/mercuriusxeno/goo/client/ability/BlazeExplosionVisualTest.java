package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.client.GooRenderTypes;
import net.minecraft.core.Direction;
import net.minecraft.util.ARGB;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * BlazeExplosionVisual's timing, and the shader pair its pipeline names.
 */
class BlazeExplosionVisualTest {

    private static final float TOLERANCE = 1e-5f;
    private static final int SAMPLES = 28;

    @Test
    void domeGrowsFromNothingToItsReachFastThenSlow() {
        assertEquals(0f, BlazeExplosionVisual.domeRadius(0f), 0f);
        assertEquals(BlazeExplosionVisual.DOME_REACH, BlazeExplosionVisual.domeRadius(1f), TOLERANCE);
        float previous = 0f;
        for (int i = 1; i <= SAMPLES; i++) {
            float radius = BlazeExplosionVisual.domeRadius(i / (float) SAMPLES);
            assertTrue(radius >= previous, "the dome shrinks at sample " + i);
            previous = radius;
        }
        float early = BlazeExplosionVisual.domeRadius(0.25f);
        float late = BlazeExplosionVisual.domeRadius(1f) - BlazeExplosionVisual.domeRadius(0.75f);
        assertTrue(early > late, "the dome does not ease out");
    }

    @Test
    void flameHoldsThenBurnsOffOverTheLastHalf() {
        assertEquals(1f, BlazeExplosionVisual.flameStrength(0f), 0f);
        assertEquals(1f, BlazeExplosionVisual.flameStrength(BlazeExplosionVisual.BURN_OFF_START), 0f);
        float midBurn = (1f + BlazeExplosionVisual.BURN_OFF_START) / 2;
        assertEquals(0.5f, BlazeExplosionVisual.flameStrength(midBurn), TOLERANCE);
        assertEquals(0f, BlazeExplosionVisual.flameStrength(1f), TOLERANCE);
    }

    @Test
    void rampMeetsTheDomesFirstDrawnFrame() {
        DomeRampShape.assertRampMeetsFirstFrame(BlazeExplosionVisual::rampRadius,
                BlazeExplosionVisual.domeRadius(1f / BlazeExplosionVisual.DURATION_TICKS),
                ramp -> ARGB.alpha(BlazeExplosionVisual.domeColor(BlazeExplosionVisual.FIRST_DRAWN_PROGRESS,
                        Direction.NORTH, DomeRamp.alpha(ramp))));
    }

    @Test
    void pipelineShadersResolveOnTheClasspath() {
        PipelineShaders.assertExist(GooRenderTypes.BLAZE_EXPLOSION);
    }
}
