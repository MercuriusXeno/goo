package com.mercuriusxeno.goo.client.model;

import com.mercuriusxeno.goo.client.ClientGooTypes;
import com.mercuriusxeno.goo.client.GooRenderUtil;
import com.mercuriusxeno.goo.client.GooSubmitter;
import com.mercuriusxeno.goo.client.RecordingVertexConsumer;
import com.mercuriusxeno.goo.client.throwing.BlobShape;
import com.mercuriusxeno.goo.client.throwing.GooFlightRenderer;
import com.mercuriusxeno.goo.type.GooTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.ToDoubleFunction;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * A goo item drawn in the level submits the palm cuboid in its stack's goo
 * type, the core ahead of the shell and both on the translucent goo type, and
 * a stack with no goo type draws nothing
 * (decisions goo-item-in-world-draws-as-the-palm-cuboid,
 * slime-core-draws-translucent-at-the-shells-opacity).
 */
class GooCuboidSpecialRendererTest {

    /** A quarter of the core's pulse, in ticks: its sine's quarter turn over its pace, its widest swell. */
    private static final float QUARTER_PULSE = (float) (Math.PI / 2 / 0.3);
    private static final float SPAN_TOLERANCE = 1e-5f;

    /** goo-cuboid-core-is-three-quarters-of-its-shell */
    @Test
    void aGooStacksCoreStaysStillAtThreeQuartersOfItsShell() {
        assertStillAtThreeQuarters(collector -> new GooCuboidSpecialRenderer().submit(
                GooCuboidSpecialRenderer.GooCuboid.ofVolume(GooTypes.ZOMBIE, 1), new PoseStack(), collector, 0, 0,
                false, 0));
    }

    @Test
    void aGooStackSubmitsOneCoreAndOneShellOfItsType() {
        SubmitNodeCollector collector = mock(SubmitNodeCollector.class);
        try (MockedStatic<Minecraft> client = mockStatic(Minecraft.class);
             MockedStatic<GooFlightRenderer> flight = mockStatic(GooFlightRenderer.class)) {
            client.when(Minecraft::getInstance).thenReturn(mock(Minecraft.class));

            new GooCuboidSpecialRenderer().submit(GooCuboidSpecialRenderer.GooCuboid.ofVolume(GooTypes.ZOMBIE, 1),
                    new PoseStack(), collector, 0, 0, false, 0);

            ArgumentCaptor<RenderType> types = ArgumentCaptor.forClass(RenderType.class);
            ArgumentCaptor<SubmitNodeCollector.CustomGeometryRenderer> geometry =
                    ArgumentCaptor.forClass(SubmitNodeCollector.CustomGeometryRenderer.class);
            verify(collector, times(2)).submitCustomGeometry(any(), types.capture(), geometry.capture());
            assertEquals(List.of(GooSubmitter.renderType(), GooSubmitter.renderType()), types.getAllValues(),
                    "the core and the shell should both submit on the translucent goo type");

            PoseStack.Pose pose = new PoseStack().last();
            VertexConsumer consumer = mock(VertexConsumer.class);
            geometry.getAllValues().getFirst().render(pose, consumer);
            flight.verify(() -> GooFlightRenderer.emitStillCore(any(), any(), eq(GooTypes.ZOMBIE), any()));
            flight.verify(() -> GooFlightRenderer.emitShell(any(), any(), any(), any()), never());
            geometry.getAllValues().getLast().render(pose, consumer);
            flight.verify(() -> GooFlightRenderer.emitShell(any(), any(), eq(GooTypes.ZOMBIE), any()));
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 1_000_000})
    void aGooStackDrawsItsCoreAndShellAtItsVolumesSize(int volume) {
        SubmitNodeCollector collector = mock(SubmitNodeCollector.class);
        try (MockedStatic<Minecraft> client = mockStatic(Minecraft.class);
             MockedStatic<GooFlightRenderer> flight = mockStatic(GooFlightRenderer.class)) {
            client.when(Minecraft::getInstance).thenReturn(mock(Minecraft.class));

            new GooCuboidSpecialRenderer().submit(GooCuboidSpecialRenderer.GooCuboid.ofVolume(GooTypes.ZOMBIE, volume),
                    new PoseStack(), collector, 0, 0, false, 0);

            ArgumentCaptor<SubmitNodeCollector.CustomGeometryRenderer> geometry =
                    ArgumentCaptor.forClass(SubmitNodeCollector.CustomGeometryRenderer.class);
            verify(collector, times(2)).submitCustomGeometry(any(), any(), geometry.capture());
            PoseStack.Pose pose = new PoseStack().last();
            VertexConsumer consumer = mock(VertexConsumer.class);
            geometry.getAllValues().forEach(drawn -> drawn.render(pose, consumer));
            BlobShape size = BlobShape.CUBE.scaled(GooCuboidSize.sizeOf(volume));
            flight.verify(() -> GooFlightRenderer.emitStillCore(any(), any(), any(), eq(size)));
            flight.verify(() -> GooFlightRenderer.emitShell(any(), any(), any(), eq(size)));
        }
    }

    /**
     * The spans along x, y and z of each geometry the collector took, rendered
     * in submission order with the game time standing at a moment.
     */
    private static List<float[]> spansDrawnAt(float time, Consumer<SubmitNodeCollector> submit) {
        SubmitNodeCollector collector = mock(SubmitNodeCollector.class);
        List<float[]> spans = new ArrayList<>();
        try (MockedStatic<Minecraft> client = mockStatic(Minecraft.class);
             MockedStatic<GloveSpecialRenderer> glove = mockStatic(GloveSpecialRenderer.class, CALLS_REAL_METHODS);
             MockedStatic<GooRenderUtil> sprites = mockStatic(GooRenderUtil.class, CALLS_REAL_METHODS);
             MockedStatic<ClientGooTypes> colors = mockStatic(ClientGooTypes.class)) {
            client.when(Minecraft::getInstance).thenReturn(mock(Minecraft.class));
            glove.when(GloveSpecialRenderer::gameTime).thenReturn(time);
            sprites.when(() -> GooRenderUtil.lookupFluidSprite(any())).thenReturn(mock(TextureAtlasSprite.class));
            colors.when(() -> ClientGooTypes.color(any())).thenReturn(0xFFFFFF);
            doAnswer(call -> {
                RecordingVertexConsumer recorder = new RecordingVertexConsumer();
                call.<SubmitNodeCollector.CustomGeometryRenderer>getArgument(2)
                        .render(call.<PoseStack>getArgument(0).last(), recorder);
                spans.add(spansOf(recorder.vertices()));
                return null;
            }).when(collector).submitCustomGeometry(any(), any(), any());
            submit.accept(collector);
        }
        return spans;
    }

    private static float[] spansOf(List<RecordingVertexConsumer.Vertex> vertices) {
        List<ToDoubleFunction<RecordingVertexConsumer.Vertex>> axes = List.of(RecordingVertexConsumer.Vertex::x,
                RecordingVertexConsumer.Vertex::y, RecordingVertexConsumer.Vertex::z);
        float[] spans = new float[axes.size()];
        for (int axis = 0; axis < spans.length; axis++) {
            spans[axis] = (float) (vertices.stream().mapToDouble(axes.get(axis)).max().orElseThrow()
                    - vertices.stream().mapToDouble(axes.get(axis)).min().orElseThrow());
        }
        return spans;
    }

    private static void assertStillAtThreeQuarters(Consumer<SubmitNodeCollector> submit) {
        for (float time : new float[] {0f, QUARTER_PULSE}) {
            List<float[]> drawn = spansDrawnAt(time, submit);
            float[] core = drawn.getFirst();
            float[] shell = drawn.getLast();
            for (int axis = 0; axis < core.length; axis++) {
                assertEquals(0.75f * shell[axis], core[axis], SPAN_TOLERANCE,
                        "the core's span on axis " + axis + " at game time " + time);
            }
        }
    }

    @Test
    void aStackWithNoGooTypeDrawsNothing() {
        SubmitNodeCollector collector = mock(SubmitNodeCollector.class);

        new GooCuboidSpecialRenderer().submit(null, new PoseStack(), collector, 0, 0, false, 0);

        verify(collector, never()).submitCustomGeometry(any(), any(), any());
    }
}
