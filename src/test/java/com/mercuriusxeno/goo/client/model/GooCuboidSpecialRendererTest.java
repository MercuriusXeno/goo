package com.mercuriusxeno.goo.client.model;

import com.mercuriusxeno.goo.client.GooSubmitter;
import com.mercuriusxeno.goo.client.throwing.BlobShape;
import com.mercuriusxeno.goo.client.throwing.GooFlightRenderer;
import com.mercuriusxeno.goo.type.GooTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * A goo item drawn in the level submits the palm cuboid in its stack's goo
 * type, the core on the solid block-atlas type and the translucent shell on
 * the goo type, and a stack with no goo type draws nothing
 * (decision goo-item-in-world-draws-as-the-palm-cuboid).
 */
class GooCuboidSpecialRendererTest {

    @Test
    void aGooStackSubmitsOneCoreAndOneShellOfItsType() {
        SubmitNodeCollector collector = mock(SubmitNodeCollector.class);
        try (MockedStatic<Minecraft> client = mockStatic(Minecraft.class);
             MockedStatic<GooFlightRenderer> flight = mockStatic(GooFlightRenderer.class)) {
            client.when(Minecraft::getInstance).thenReturn(mock(Minecraft.class));

            new GooCuboidSpecialRenderer().submit(GooCuboidSpecialRenderer.GooCuboid.ofVolume(GooTypes.ZOO, 1),
                    new PoseStack(), collector, 0, 0, false, 0);

            ArgumentCaptor<RenderType> types = ArgumentCaptor.forClass(RenderType.class);
            ArgumentCaptor<SubmitNodeCollector.CustomGeometryRenderer> geometry =
                    ArgumentCaptor.forClass(SubmitNodeCollector.CustomGeometryRenderer.class);
            verify(collector, times(2)).submitCustomGeometry(any(), types.capture(), geometry.capture());
            assertEquals(List.of(GooSubmitter.solidOnBlockAtlas(), GooSubmitter.renderType()), types.getAllValues(),
                    "the core should submit on the solid block-atlas type and the shell on the goo type");

            PoseStack.Pose pose = new PoseStack().last();
            VertexConsumer consumer = mock(VertexConsumer.class);
            geometry.getAllValues().forEach(drawn -> drawn.render(pose, consumer));
            flight.verify(() -> GooFlightRenderer.emitCore(any(), any(), eq(GooTypes.ZOO), anyFloat(), any()));
            flight.verify(() -> GooFlightRenderer.emitShell(any(), any(), eq(GooTypes.ZOO), any()));
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 1_000_000})
    void aGooStackDrawsItsCoreAndShellAtItsVolumesSize(int volume) {
        SubmitNodeCollector collector = mock(SubmitNodeCollector.class);
        try (MockedStatic<Minecraft> client = mockStatic(Minecraft.class);
             MockedStatic<GooFlightRenderer> flight = mockStatic(GooFlightRenderer.class)) {
            client.when(Minecraft::getInstance).thenReturn(mock(Minecraft.class));

            new GooCuboidSpecialRenderer().submit(GooCuboidSpecialRenderer.GooCuboid.ofVolume(GooTypes.ZOO, volume),
                    new PoseStack(), collector, 0, 0, false, 0);

            ArgumentCaptor<SubmitNodeCollector.CustomGeometryRenderer> geometry =
                    ArgumentCaptor.forClass(SubmitNodeCollector.CustomGeometryRenderer.class);
            verify(collector, times(2)).submitCustomGeometry(any(), any(), geometry.capture());
            PoseStack.Pose pose = new PoseStack().last();
            VertexConsumer consumer = mock(VertexConsumer.class);
            geometry.getAllValues().forEach(drawn -> drawn.render(pose, consumer));
            BlobShape size = BlobShape.CUBE.scaled(GooCuboidSize.sizeOf(volume));
            flight.verify(() -> GooFlightRenderer.emitCore(any(), any(), any(), anyFloat(), eq(size)));
            flight.verify(() -> GooFlightRenderer.emitShell(any(), any(), any(), eq(size)));
        }
    }

    @Test
    void aStackWithNoGooTypeDrawsNothing() {
        SubmitNodeCollector collector = mock(SubmitNodeCollector.class);

        new GooCuboidSpecialRenderer().submit(null, new PoseStack(), collector, 0, 0, false, 0);

        verify(collector, never()).submitCustomGeometry(any(), any(), any());
    }
}
