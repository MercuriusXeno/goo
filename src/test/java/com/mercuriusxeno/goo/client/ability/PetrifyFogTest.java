package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.client.GooRenderTypes;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Petrify's fog spreads its cross-sections evenly from just clear of the
 * view to the cone's reach, and its pipelines, the fog, the stone patches
 * and the block mingle, name shaders the classpath holds
 * (decision petrify-stone-encasement-and-calcify-map).
 */
class PetrifyFogTest {

    private static final double RANGE = 6;
    private static final double DELTA = 1e-9;

    @Test
    void sectionsSpreadEvenlyInsideTheReach() {
        double first = PetrifyFog.sectionDistance(0, RANGE);
        double last = PetrifyFog.sectionDistance(PetrifyFog.SECTIONS - 1, RANGE);
        assertTrue(first > PetrifyFog.NEAR);
        assertTrue(last < RANGE);
        double step = PetrifyFog.sectionDistance(1, RANGE) - first;
        assertEquals(step, last - PetrifyFog.sectionDistance(PetrifyFog.SECTIONS - 2, RANGE), DELTA);
    }

    @Test
    void pipelineShadersResolveOnTheClasspath() {
        PipelineShaders.assertExist(GooRenderTypes.PETRIFY_FOG);
        PipelineShaders.assertExist(GooRenderTypes.PETRIFY_STONE);
        PipelineShaders.assertExist(GooRenderTypes.BLOCK_MINGLE);
    }
}
