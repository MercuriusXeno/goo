package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.ability.program.LingerStep;
import com.mercuriusxeno.goo.ability.program.PhasedStep;
import com.mercuriusxeno.goo.ability.program.StepPhase;
import com.mercuriusxeno.goo.ability.program.Variables;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mojang.blaze3d.pipeline.BlendFunction;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * NetherExplosionVisual's inward rush: it spans the black hole's gather,
 * falling from the implode radius onto the marker, drawn as black streaks
 * blended over the world, and the shader pair its pipeline names; reads the
 * rush's fragment shader through the classpath.
 */
class NetherExplosionVisualTest {

    private static final String BLACK_HOLE = "void_dark_star";
    private static final String RUSH_SHADER = "/assets/goo/shaders/core/nether_explosion.fsh";
    /** A radius a drag sets, in blocks. */
    private static final double DRAGGED = 4;
    private static final float START = 5f;
    private static final float TOLERANCE = 1e-5f;
    private static final int SAMPLES = 30;

    @Test
    void rushSpansTheBlackHolesGather() {
        PhasedStep program = (PhasedStep) LingerStep.bodyOf(AbilityJson.decode(BLACK_HOLE).behaviors())
                .orElseThrow().getFirst();
        StepPhase gather = program.phases().getFirst();
        assertEquals("gather", gather.name());
        assertEquals(NetherExplosionVisual.DURATION_TICKS, gather.ticks().evaluateInt(Variables.NONE));
    }

    /**
     * anti-moves-from-nether-to-void: every Void landing announces a Void
     * burnout, so the rush draws only for a program holding the phased
     * gather, the black hole's, and Hole's and Erase's landings draw none.
     */
    @Test
    void rushStartsFromTheDraggedRadiusAndDrawsForNoOtherVoidLanding() {
        assertEquals(DRAGGED, NetherExplosionVisual.rushReach(AbilityJson.decode(BLACK_HOLE).behaviors(), DRAGGED)
                .orElseThrow(), TOLERANCE);
        assertTrue(NetherExplosionVisual.rushReach(AbilityJson.decode("void_hole").behaviors(), DRAGGED).isEmpty());
        assertTrue(NetherExplosionVisual.rushReach(AbilityJson.decode("void_erase").behaviors(), DRAGGED).isEmpty());
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

    /** black-hole-build-up-draws-black: black adds nothing, so the rush blends over the world. */
    @Test
    void rushBlendsSoBlackShows() {
        assertEquals(Optional.of(BlendFunction.TRANSLUCENT),
                GooRenderTypes.NETHER_EXPLOSION.getColorTargetState().blendFunction());
    }

    /** black-hole-build-up-draws-black: the streaks draw black, with no red ramp and no white-hot end. */
    @Test
    void rushStreaksDrawBlack() throws IOException {
        String shader = rushShader();
        for (String gone : List.of("8B0000", "B32828", "vec3(0.545", "vec3(0.702", "HOT_COLOR", "white-hot")) {
            assertFalse(shader.contains(gone), "the rush's shader still reads " + gone);
        }
        assertTrue(shader.contains("const vec3 STREAK_COLOR = vec3(0.0);"), "the streak color is not black");
        assertTrue(shader.contains("fragColor = vec4(STREAK_COLOR,"), "the streaks draw some other color");
    }

    private static String rushShader() throws IOException {
        try (InputStream in = NetherExplosionVisualTest.class.getResourceAsStream(RUSH_SHADER)) {
            assertNotNull(in, RUSH_SHADER);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
