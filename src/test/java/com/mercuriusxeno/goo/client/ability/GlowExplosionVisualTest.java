package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.client.RecordingVertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
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
    private static final float DOME_LIFT = -0.5f;
    private static final float BLOCK_CENTER = 0.5f;

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

    @Test
    void renderedDomeDrawsAtItsReach() {
        RecordingVertexConsumer consumer = new RecordingVertexConsumer();
        MultiBufferSource.BufferSource buffers = mock(MultiBufferSource.BufferSource.class);
        when(buffers.getBuffer(any())).thenReturn(consumer);
        float gameTime = START + GlowExplosionVisual.DURATION_TICKS;

        GlowExplosionVisual.INSTANCE.render(burnout(),
                new BurnoutFrame(new PoseStack(), buffers, Vec3.ZERO, gameTime));

        assertEquals(GlowExplosionVisual.DOME_REACH, farthestFromDomeCenter(consumer.vertices()), TOLERANCE);
    }

    @Test
    void pipelineShadersResolveOnTheClasspath() {
        PipelineShaders.assertExist(GooRenderTypes.GLOW_EXPLOSION);
    }

    private static ChainBurnouts.Burnout burnout() {
        return new ChainBurnouts.Burnout(BlockPos.ZERO, FACE, "goo:glow_test", START,
                GlowExplosionVisual.INSTANCE);
    }

    private static float farthestFromDomeCenter(List<RecordingVertexConsumer.Vertex> vertices) {
        float cx = BLOCK_CENTER + FACE.getStepX() * DOME_LIFT;
        float cy = BLOCK_CENTER + FACE.getStepY() * DOME_LIFT;
        float cz = BLOCK_CENTER + FACE.getStepZ() * DOME_LIFT;
        double farthest = 0;
        for (RecordingVertexConsumer.Vertex v : vertices) {
            farthest = Math.max(farthest, Math.sqrt((v.x() - cx) * (v.x() - cx) + (v.y() - cy) * (v.y() - cy)
                    + (v.z() - cz) * (v.z() - cz)));
        }
        return (float) farthest;
    }
}
