package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.block.ability.GlowCrystalBlock;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.client.RecordingVertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * GlowExplosionVisual's timing, its reach per stack count on screen, and
 * the shader pair its pipeline names.
 */
class GlowExplosionVisualTest {

    private static final float TOLERANCE = 1e-5f;
    private static final int LARGE_STACKS = 4;
    private static final Direction FACE = Direction.EAST;
    private static final long START = 1_000L;
    private static final float DOME_LIFT = -0.5f;
    private static final float BLOCK_CENTER = 0.5f;

    @Test
    void domeRisesToItsReachFastThenSlow() {
        assertEquals(0f, GlowExplosionVisual.domeRadius(0f, LARGE_STACKS), 0f);
        float early = GlowExplosionVisual.domeRadius(0.25f, LARGE_STACKS);
        float late = GlowExplosionVisual.domeRadius(1f, LARGE_STACKS)
                - GlowExplosionVisual.domeRadius(0.75f, LARGE_STACKS);
        assertTrue(early > late, "the dome does not ease out");
    }

    @Test
    void largeCrystalDomeReachesAQuarterPastOneBlock() {
        assertEquals(1.25f, GlowExplosionVisual.domeRadius(1f, LARGE_STACKS), TOLERANCE);
    }

    @Test
    void domeReachRisesWithEachStackInStepWithTheCrystalsExtent() {
        float largeExtent = extent(GlowCrystalBlock.CrystalSize.LARGE);
        float previous = 0f;
        for (int stacks = 1; stacks <= LARGE_STACKS; stacks++) {
            float radius = GlowExplosionVisual.domeRadius(1f, stacks);
            float expected = 1.25f * extent(GlowCrystalBlock.CrystalSize.fromStacks(stacks)) / largeExtent;
            assertEquals(expected, radius, TOLERANCE, "stack " + stacks + " misses its crystal's ratio");
            assertTrue(radius > previous, "stack " + stacks + " does not reach past the stack below");
            previous = radius;
        }
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

    @ParameterizedTest
    @ValueSource(ints = {1, 4})
    void renderedDomeDrawsAtTheBurnoutsStackCount(int stacks) {
        RecordingVertexConsumer consumer = new RecordingVertexConsumer();
        MultiBufferSource.BufferSource buffers = mock(MultiBufferSource.BufferSource.class);
        when(buffers.getBuffer(any())).thenReturn(consumer);
        float gameTime = START + GlowExplosionVisual.DURATION_TICKS;

        GlowExplosionVisual.INSTANCE.render(burnout(stacks),
                new BurnoutFrame(new PoseStack(), buffers, Vec3.ZERO, gameTime));

        assertEquals(GlowExplosionVisual.domeReach(stacks), farthestFromDomeCenter(consumer.vertices()), TOLERANCE);
    }

    @Test
    void pipelineShadersResolveOnTheClasspath() {
        PipelineShaders.assertExist(GooRenderTypes.GLOW_EXPLOSION);
    }

    private static ChainBurnouts.Burnout burnout(int stacks) {
        return new ChainBurnouts.Burnout(BlockPos.ZERO, FACE, "goo:glow_test", stacks, START,
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

    private static float extent(GlowCrystalBlock.CrystalSize size) {
        return (float) (size.max - size.min);
    }
}
