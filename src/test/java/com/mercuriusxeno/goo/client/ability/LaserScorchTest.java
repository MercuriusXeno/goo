package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Laser's scorch draws every mark's char before any mark's glow, so no
 * mark's char ring darkens its neighbor's glow and a trail reads as one
 * blended streak rather than stacked decals.
 * decision sunbeam-leaves-a-glowing-scorch-that-trails-and-fades
 */
class LaserScorchTest {

    /**
     * The scorch draws at the stage right after the solid blocks it marks,
     * ahead of every block entity's beam and the Laser ray, so a beam
     * passing in front of a mark lies over it (operator UAT 2026-11-10).
     */
    @Test
    void theScorchDrawsRightAfterTheSolidBlocksBeforeAnyBeam() {
        List<Class<?>> stages = Arrays.stream(LaserScorch.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(SubscribeEvent.class))
                .map(method -> method.getParameterTypes()[0])
                .toList();
        assertEquals(List.of(RenderLevelStageEvent.AfterOpaqueBlocks.class), stages);
    }

    @Test
    void everyMarksCharDrawsBeforeAnyMarksGlow() {
        MultiBufferSource.BufferSource buffers = mock(MultiBufferSource.BufferSource.class);
        VertexConsumer charConsumer = mock(VertexConsumer.class);
        VertexConsumer glowConsumer = mock(VertexConsumer.class);
        when(buffers.getBuffer(GooRenderTypes.LASER_SCORCH_TYPE)).thenReturn(charConsumer);
        when(buffers.getBuffer(GooRenderTypes.LASER_SCORCH_GLOW_TYPE)).thenReturn(glowConsumer);
        List<VertexConsumer> drawnInto = new ArrayList<>();
        LaserScorch.drawInTwoPasses(buffers, drawnInto::add);
        assertEquals(List.of(charConsumer, glowConsumer), drawnInto);
        InOrder order = inOrder(buffers);
        order.verify(buffers).getBuffer(GooRenderTypes.LASER_SCORCH_TYPE);
        order.verify(buffers).endBatch(GooRenderTypes.LASER_SCORCH_TYPE);
        order.verify(buffers).getBuffer(GooRenderTypes.LASER_SCORCH_GLOW_TYPE);
        order.verify(buffers).endBatch(GooRenderTypes.LASER_SCORCH_GLOW_TYPE);
    }
}
