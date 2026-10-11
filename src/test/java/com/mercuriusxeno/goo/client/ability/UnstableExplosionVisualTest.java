package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.ability.program.ExplodeStep;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mojang.blaze3d.platform.CompareOp;
import net.minecraft.util.ARGB;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UnstableExplosionVisual's timing, and the shader pair its pipeline names.
 */
class UnstableExplosionVisualTest {

    private static final float REACH = 4f;
    private static final float TOLERANCE = 1e-5f;
    private static final int SAMPLES = 32;

    @Test
    void fireballGrowsFromNothingToTheBlastRadius() {
        assertEquals(0f, UnstableExplosionVisual.sphereRadius(0f, REACH), 0f);
        assertEquals(REACH, UnstableExplosionVisual.sphereRadius(1f, REACH), TOLERANCE);
        float previous = 0f;
        for (int i = 1; i <= SAMPLES; i++) {
            float radius = UnstableExplosionVisual.sphereRadius(i / (float) SAMPLES, REACH);
            assertTrue(radius >= previous, "the fireball shrinks at sample " + i);
            previous = radius;
        }
    }

    @Test
    void fireballGrowsFastThenSlow() {
        float early = UnstableExplosionVisual.sphereRadius(0.25f, REACH);
        float late = UnstableExplosionVisual.sphereRadius(1f, REACH) - UnstableExplosionVisual.sphereRadius(0.75f, REACH);
        assertTrue(early > late, "the fireball does not ease out");
    }

    @Test
    void ringRunsAheadOfTheFireball() {
        for (int i = 1; i <= SAMPLES; i++) {
            float progress = i / (float) SAMPLES;
            assertTrue(UnstableExplosionVisual.ringRadius(progress, REACH)
                            > UnstableExplosionVisual.sphereRadius(progress, REACH),
                    "the ring falls behind at progress " + progress);
        }
        assertEquals(REACH * UnstableExplosionVisual.RING_REACH,
                UnstableExplosionVisual.ringRadius(1f, REACH), TOLERANCE);
    }

    @Test
    void pipelineShadersResolveOnTheClasspath() {
        PipelineShaders.assertExist(GooRenderTypes.UNSTABLE_EXPLOSION);
    }

    /**
     * A held ghost's sphere sets blue, which a landing's sphere leaves clear, so the
     * shader holds it unfaded (decision held-visual-ghosts-the-landing-in-two-passes).
     */
    @Test
    void heldSphereIsMarkedApartFromALandingSphere() {
        assertEquals(0xFF, ARGB.blue(UnstableExplosionVisual.heldSphereColor(0x66)));
        assertEquals(0, ARGB.green(UnstableExplosionVisual.heldSphereColor(0x66)));
        assertEquals(0x66, ARGB.alpha(UnstableExplosionVisual.heldSphereColor(0x66)));
        assertEquals(0, ARGB.blue(UnstableExplosionVisual.sphereColor(0.5f, 0xFF)));
    }

    /**
     * A burnout carries the size its cast was dragged to, and the burst fills
     * that sphere rather than a resting power's
     * (decision blast-is-drag-sized-like-the-black-hole).
     */
    @Test
    void burstReachFollowsTheCastsSize() {
        ExplodeStep blast = AbilityJson.decode("unstable_explode").behaviors().stream()
                .filter(ExplodeStep.class::isInstance).map(ExplodeStep.class::cast).findFirst().orElseThrow();

        assertEquals(6f, UnstableExplosionVisual.blastReach(blast, 6), TOLERANCE);
        assertEquals(3f, UnstableExplosionVisual.blastReach(blast, 3), TOLERANCE);
    }

    @Test
    void throughBlocksPipelineIgnoresDepthOverTheUnstableShader() {
        PipelineShaders.assertExist(GooRenderTypes.UNSTABLE_EXPLOSION_THROUGH_BLOCKS);
        assertEquals(CompareOp.ALWAYS_PASS,
                GooRenderTypes.UNSTABLE_EXPLOSION_THROUGH_BLOCKS.getDepthStencilState().depthTest());
    }
}
