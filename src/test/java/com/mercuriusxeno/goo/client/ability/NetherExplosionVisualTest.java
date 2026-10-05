package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.ability.program.LingerStep;
import com.mercuriusxeno.goo.ability.program.PhasedStep;
import com.mercuriusxeno.goo.ability.program.StepPhase;
import com.mercuriusxeno.goo.ability.program.Variables;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * NetherExplosionVisual's inward rush: it spans the black hole's gather,
 * falling from the implode radius onto the marker, and the shader pair its
 * pipeline names.
 */
class NetherExplosionVisualTest {

    private static final float START = 5f;
    private static final float TOLERANCE = 1e-5f;
    private static final int SAMPLES = 30;

    @Test
    void rushSpansTheBlackHolesGather() {
        PhasedStep program = (PhasedStep) LingerStep.bodyOf(AbilityJson.decode("nether_black_hole").behaviors())
                .orElseThrow().getFirst();
        StepPhase gather = program.phases().getFirst();
        assertEquals("gather", gather.name());
        assertEquals(NetherExplosionVisual.DURATION_TICKS, gather.ticks().evaluateInt(Variables.NONE));
    }

    @Test
    void rushFallsFromTheImplodeRadiusOntoTheMarkerSlowThenFast() {
        assertEquals(START, NetherExplosionVisual.rushRadius(0f, START), 0f);
        assertEquals(0f, NetherExplosionVisual.rushRadius(1f, START), TOLERANCE);
        float previous = START;
        for (int i = 1; i <= SAMPLES; i++) {
            float radius = NetherExplosionVisual.rushRadius(i / (float) SAMPLES, START);
            assertTrue(radius <= previous, "the rush widens at sample " + i);
            previous = radius;
        }
        float early = START - NetherExplosionVisual.rushRadius(0.25f, START);
        float late = NetherExplosionVisual.rushRadius(0.75f, START) - NetherExplosionVisual.rushRadius(1f, START);
        assertTrue(late > early, "the rush does not speed up as it falls in");
    }

    @Test
    void rushFadesInRatherThanPoppingIn() {
        float fadeInEnd = NetherExplosionVisual.FADE_IN_END;
        assertEquals(0f, NetherExplosionVisual.strength(0f), 0f);
        assertEquals(5f, fadeInEnd * NetherExplosionVisual.DURATION_TICKS, TOLERANCE);
        float previous = 0f;
        for (int i = 1; i <= SAMPLES; i++) {
            float strength = NetherExplosionVisual.strength(fadeInEnd * i / SAMPLES);
            assertTrue(strength > previous, "the rush stops fading in at sample " + i);
            previous = strength;
        }
        assertTrue(NetherExplosionVisual.strength(fadeInEnd / 2) < 0.5f, "the rush jumps in");
        assertEquals(1f, NetherExplosionVisual.strength(fadeInEnd), TOLERANCE);
    }

    @Test
    void rushHoldsThenFadesAsItReachesTheCenter() {
        assertEquals(1f, NetherExplosionVisual.strength(NetherExplosionVisual.FADE_START), 0f);
        float midFade = (1f + NetherExplosionVisual.FADE_START) / 2;
        assertEquals(0.5f, NetherExplosionVisual.strength(midFade), TOLERANCE);
        assertEquals(0f, NetherExplosionVisual.strength(1f), TOLERANCE);
    }

    @Test
    void pipelineShadersResolveOnTheClasspath() {
        PipelineShaders.assertExist(GooRenderTypes.NETHER_EXPLOSION);
    }
}
