package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.client.RecordingVertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * GlowExplosionVisual's timing, its reach over Bulb's one crystal, on screen, and the shader pair its pipeline names.
 */
class GlowExplosionVisualTest {

    private static final float TOLERANCE = 1e-5f;
    private static final Direction FACE = Direction.EAST;
    private static final long START = 1_000L;
    private static final float BLOCK_CENTER = 0.5f;
    /** How far out along each axis a whole sphere reaches, as a share of its radius. */
    private static final float WHOLE_SPHERE_SHARE = 0.9f;
    /** Half of a byte's full alpha. */
    private static final int HALF_ALPHA = 0x80;

    @Test
    void domeRisesToItsReachFastThenSlow() {
        assertEquals(0f, GlowExplosionVisual.domeRadius(0f), 0f);
        float early = GlowExplosionVisual.domeRadius(0.25f);
        float late = GlowExplosionVisual.domeRadius(1f)
                - GlowExplosionVisual.domeRadius(0.75f);
        assertTrue(early > late, "the dome does not ease out");
    }

    // decision bulb-one-model-max-light-beacon-combo
    @Test
    void domeReachesItsFullReachOverTheOneCrystal() {
        assertEquals(1.25f, GlowExplosionVisual.domeRadius(1f), TOLERANCE);
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

    // decision burnouts-are-whole-spheres
    @Test
    void aWallLandingBurnsOutAsAWholeSphereAboutTheCell() {
        RecordingVertexConsumer consumer = new RecordingVertexConsumer();
        MultiBufferSource.BufferSource buffers = mock(MultiBufferSource.BufferSource.class);
        when(buffers.getBuffer(any())).thenReturn(consumer);
        float gameTime = START + GlowExplosionVisual.DURATION_TICKS;

        GlowExplosionVisual.INSTANCE.render(burnout(),
                new BurnoutFrame(new PoseStack(), buffers, Vec3.ZERO, gameTime));

        float reach = GlowExplosionVisual.DOME_REACH * WHOLE_SPHERE_SHARE;
        List<RecordingVertexConsumer.Vertex> vertices = consumer.vertices();
        assertTrue(vertices.stream().anyMatch(v -> v.y() > BLOCK_CENTER + reach), "nothing above the cell");
        assertTrue(vertices.stream().anyMatch(v -> v.y() < BLOCK_CENTER - reach), "nothing below the cell");
        assertTrue(vertices.stream().anyMatch(v -> v.x() > BLOCK_CENTER + reach), "nothing east of the cell");
        assertTrue(vertices.stream().anyMatch(v -> v.x() < BLOCK_CENTER - reach), "nothing west of the cell");
        assertTrue(vertices.stream().anyMatch(v -> v.z() > BLOCK_CENTER + reach), "nothing south of the cell");
        assertTrue(vertices.stream().anyMatch(v -> v.z() < BLOCK_CENTER - reach), "nothing north of the cell");
    }

    /**
     * Bulb's landing dome renders at half of full opacity at every point of
     * its 20 ticks, as the operator's try asks
     * (decision bulb-spheroid-draws-at-half-opacity).
     */
    @ParameterizedTest
    @ValueSource(floats = {0f, 0.3f, 1f})
    void domeRendersAtHalfOpacity(float progress) {
        RecordingVertexConsumer consumer = new RecordingVertexConsumer();
        MultiBufferSource.BufferSource buffers = mock(MultiBufferSource.BufferSource.class);
        when(buffers.getBuffer(any())).thenReturn(consumer);
        float gameTime = START + progress * GlowExplosionVisual.DURATION_TICKS;

        GlowExplosionVisual.INSTANCE.render(burnout(), new BurnoutFrame(new PoseStack(), buffers, Vec3.ZERO, gameTime));

        assertTrue(consumer.vertices().stream().allMatch(v -> Math.abs(ARGB.alpha(v.color()) - HALF_ALPHA) <= 1),
                "the dome should render at alpha " + HALF_ALPHA);
    }

    @Test
    void pipelineShadersResolveOnTheClasspath() {
        PipelineShaders.assertExist(GooRenderTypes.GLOW_EXPLOSION);
    }

    private static ChainBurnouts.Burnout burnout() {
        return new ChainBurnouts.Burnout(BlockPos.ZERO, FACE, "goo:glow_test", 0, START,
                GlowExplosionVisual.INSTANCE);
    }
}
