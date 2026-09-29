package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.client.GooRenderTypes;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CrystalExplosionVisual's timing, and the shader pair its pipeline names.
 */
class CrystalExplosionVisualTest {

    private static final float REACH = 4.5f;
    private static final float TOLERANCE = 1e-5f;

    private static float progressAt(float tick) {
        return tick / CrystalExplosionVisual.DURATION_TICKS;
    }

    @Test
    void shellGrowsToTheCloudRadiusOverTheExpandFastThenSlow() {
        assertEquals(0f, CrystalExplosionVisual.shellRadius(0f, REACH), 0f);
        float grown = progressAt(CrystalExplosionVisual.GROW_TICKS);
        assertEquals(REACH, CrystalExplosionVisual.shellRadius(grown, REACH), TOLERANCE);
        assertEquals(REACH, CrystalExplosionVisual.shellRadius(1f, REACH), TOLERANCE);
        float quarter = CrystalExplosionVisual.GROW_TICKS / 4f;
        float early = CrystalExplosionVisual.shellRadius(progressAt(quarter), REACH);
        float late = REACH - CrystalExplosionVisual.shellRadius(progressAt(3 * quarter), REACH);
        assertTrue(early > late, "the shell does not ease out");
    }

    @Test
    void shellShattersOnlyAfterItHasGrown() {
        assertEquals(0f, CrystalExplosionVisual.shattered(0f), 0f);
        assertEquals(0f, CrystalExplosionVisual.shattered(progressAt(CrystalExplosionVisual.GROW_TICKS)), TOLERANCE);
        float midShatter = CrystalExplosionVisual.GROW_TICKS + CrystalExplosionVisual.SHATTER_TICKS / 2f;
        assertEquals(0.5f, CrystalExplosionVisual.shattered(progressAt(midShatter)), TOLERANCE);
        assertEquals(1f, CrystalExplosionVisual.shattered(1f), TOLERANCE);
    }

    @Test
    void pipelineShadersResolveOnTheClasspath() {
        PipelineShaders.assertExist(GooRenderTypes.CRYSTAL_EXPLOSION);
    }
}
