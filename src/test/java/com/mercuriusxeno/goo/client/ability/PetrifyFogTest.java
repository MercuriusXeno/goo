package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.client.GooRenderTypes;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Petrify's fog spreads its cross-sections evenly from right at the glove,
 * nearer than Bore's start, to the cone's reach, and its pipelines, the fog, the stone patches
 * and the block mingle, name shaders the classpath holds
 * (decision petrify-stone-encasement-and-calcify-map).
 */
class PetrifyFogTest {

    private static final double RANGE = 6;
    private static final double DELTA = 1e-9;

    @Test
    void sectionsSpreadEvenlyInsideTheReach() {
        int sections = PetrifyFog.SECTIONS;
        double near = PetrifyFog.FROM_THE_GLOVE;
        double first = ConeSections.sectionDistance(0, near, RANGE, sections);
        double last = ConeSections.sectionDistance(sections - 1, near, RANGE, sections);
        assertTrue(first > near);
        assertTrue(first < ConeSections.NEAR);
        assertTrue(last < RANGE);
        double step = ConeSections.sectionDistance(1, near, RANGE, sections) - first;
        assertEquals(step, last - ConeSections.sectionDistance(sections - 2, near, RANGE, sections), DELTA);
    }

    @Test
    void pipelineShadersResolveOnTheClasspath() {
        PipelineShaders.assertExist(GooRenderTypes.PETRIFY_FOG);
        PipelineShaders.assertExist(GooRenderTypes.PETRIFY_STONE);
        PipelineShaders.assertExist(GooRenderTypes.BLOCK_MINGLE);
        PipelineShaders.assertExist(GooRenderTypes.BORE_VORTEX);
    }
}
