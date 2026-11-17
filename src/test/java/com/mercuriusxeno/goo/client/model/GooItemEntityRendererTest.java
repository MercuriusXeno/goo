package com.mercuriusxeno.goo.client.model;

import com.mercuriusxeno.goo.client.ClientGooTypes;
import com.mercuriusxeno.goo.client.GooRenderUtil;
import com.mercuriusxeno.goo.client.GooSubmitter;
import com.mercuriusxeno.goo.client.RecordingVertexConsumer;
import com.mercuriusxeno.goo.client.throwing.GooFlightRenderer;
import com.mercuriusxeno.goo.type.GooTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.ToDoubleFunction;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A dropped goo item draws its cuboid resting on the entity's feet, its faces
 * square to the world's axes, the same at every age: no bob, no hover and no
 * spin (operator ruling: "Bob, hover, and spin, and also they should stay
 * aligned to the world grid", decision flecks-move-by-flexible-goo-physics-and-stay-items).
 */
class GooItemEntityRendererTest {

    private static final float WITHIN = 1e-5f;
    private static final float SIZE = 2f;
    private static final int VERTICES_PER_FACE = 4;
    /** The most a resting cuboid stands above its feet: almost flush, in blocks. */
    private static final float ALMOST_FLUSH = 0.001f;
    /** The shadow radius vanilla ItemEntityRenderer's constructor sets. */
    private static final float VANILLA_ITEM_SHADOW = 0.15f;
    /** A quarter of the core's pulse, in ticks: its sine's quarter turn over its pace, its widest swell. */
    private static final float QUARTER_PULSE = (float) (Math.PI / 2 / 0.3);
    private static final float SPAN_TOLERANCE = 1e-5f;

    @Test
    void aDroppedGooItemDrawsTheSameAxisAlignedCuboidOnItsFeetAtEveryAge() {
        try (MockedStatic<Minecraft> client = mockStatic(Minecraft.class);
             MockedStatic<GooRenderUtil> sprites = mockStatic(GooRenderUtil.class);
             MockedStatic<ClientGooTypes> colors = mockStatic(ClientGooTypes.class)) {
            client.when(Minecraft::getInstance).thenReturn(mock(Minecraft.class));
            sprites.when(() -> GooRenderUtil.lookupFluidSprite(any())).thenReturn(mock(TextureAtlasSprite.class));
            colors.when(() -> ClientGooTypes.color(any())).thenReturn(0xFFFFFF);
            GooItemEntityRenderer renderer = new GooItemEntityRenderer(context());

            List<Vector3f> young = verticesDrawnAt(renderer, 3f, 0.4f);
            List<Vector3f> old = verticesDrawnAt(renderer, 57f, 2.9f);

            assertFalse(young.isEmpty(), "the goo item should draw its cuboid");
            assertEquals(young.size(), old.size());
            for (int i = 0; i < young.size(); i++) {
                assertEquals(0, young.get(i).distance(old.get(i)), WITHIN,
                        "the cuboid should read the same at every age: no bob and no spin");
            }
            for (int face = 0; face < young.size(); face += VERTICES_PER_FACE) {
                assertTrue(squareToAnAxis(young.subList(face, face + VERTICES_PER_FACE)),
                        "every face should lie on a plane square to a world axis");
            }
            float lowest = (float) young.stream().mapToDouble(Vector3f::y).min().orElseThrow();
            assertTrue(lowest > WITHIN && lowest <= ALMOST_FLUSH + WITHIN, "the cuboid's bottom should stand "
                    + "almost flush on the entity's feet, clear of the floor's face so the two never z-fight but no "
                    + "more than " + ALMOST_FLUSH + " blocks above it; it stands at " + lowest);
        }
    }

    /** slime-core-draws-translucent-at-the-shells-opacity */
    @Test
    void aDroppedGooItemSubmitsItsCoreOnTheGooTypeAheadOfItsShell() {
        GooItemEntityRenderer renderer = new GooItemEntityRenderer(context());
        GooItemEntityRenderer.GooItemRenderState state = renderer.createRenderState();
        state.gooType = GooTypes.ZOO;
        state.gooSize = SIZE;
        SubmitNodeCollector collector = mock(SubmitNodeCollector.class);
        try (MockedStatic<Minecraft> client = mockStatic(Minecraft.class);
             MockedStatic<GooFlightRenderer> flight = mockStatic(GooFlightRenderer.class)) {
            client.when(Minecraft::getInstance).thenReturn(mock(Minecraft.class));

            renderer.submit(state, new PoseStack(), collector, new CameraRenderState());

            ArgumentCaptor<RenderType> types = ArgumentCaptor.forClass(RenderType.class);
            ArgumentCaptor<SubmitNodeCollector.CustomGeometryRenderer> geometry =
                    ArgumentCaptor.forClass(SubmitNodeCollector.CustomGeometryRenderer.class);
            verify(collector, times(2)).submitCustomGeometry(any(), types.capture(), geometry.capture());
            assertEquals(List.of(GooSubmitter.renderType(), GooSubmitter.renderType()), types.getAllValues(),
                    "the core and the shell should both submit on the translucent goo type");
            PoseStack.Pose pose = new PoseStack().last();
            geometry.getAllValues().getFirst().render(pose, mock(VertexConsumer.class));
            flight.verify(() -> GooFlightRenderer.emitStillCore(any(), any(), any(), any()));
            flight.verify(() -> GooFlightRenderer.emitShell(any(), any(), any(), any()), never());
        }
    }

    /** goo-cuboid-core-is-three-quarters-of-its-shell */
    @Test
    void aDroppedGooItemsCoreStaysStillAtThreeQuartersOfItsShell() {
        GooItemEntityRenderer renderer = new GooItemEntityRenderer(context());
        GooItemEntityRenderer.GooItemRenderState state = renderer.createRenderState();
        state.gooType = GooTypes.ZOO;
        state.gooSize = SIZE;
        assertStillAtThreeQuarters(collector -> renderer.submit(state, new PoseStack(), collector,
                new CameraRenderState()));
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
    void aDroppedGooItemCastsNoShadow() {
        GooItemEntityRenderer renderer = new GooItemEntityRenderer(context());
        GooItemEntityRenderer.GooItemRenderState state = renderer.createRenderState();
        state.gooType = GooTypes.ZOO;
        state.gooSize = SIZE;

        assertEquals(0f, renderer.getShadowRadius(state), "a dropped goo item should cast no entity shadow");
    }

    @Test
    void anyOtherDroppedItemCastsVanillasShadow() {
        GooItemEntityRenderer renderer = new GooItemEntityRenderer(context());
        GooItemEntityRenderer.GooItemRenderState state = renderer.createRenderState();
        state.gooType = null;

        assertEquals(VANILLA_ITEM_SHADOW, renderer.getShadowRadius(state),
                "an item that is not goo should cast vanilla ItemEntityRenderer's shadow");
    }

    private static boolean squareToAnAxis(List<Vector3f> face) {
        for (int axis = 0; axis < 3; axis++) {
            float first = face.get(0).get(axis);
            int on = axis;
            if (face.stream().allMatch(vertex -> Math.abs(vertex.get(on) - first) < WITHIN)) {
                return true;
            }
        }
        return false;
    }

    private static List<Vector3f> verticesDrawnAt(GooItemEntityRenderer renderer, float age, float bobOffset) {
        GooItemEntityRenderer.GooItemRenderState state = renderer.createRenderState();
        state.ageInTicks = age;
        state.bobOffset = bobOffset;
        state.shouldBob = true;
        state.gooType = GooTypes.ZOO;
        state.gooSize = SIZE;
        SubmitNodeCollector collector = mock(SubmitNodeCollector.class);
        List<PoseStack.Pose> poses = new ArrayList<>();
        List<SubmitNodeCollector.CustomGeometryRenderer> geometry = new ArrayList<>();
        doAnswer(call -> {
            PoseStack snapshot = new PoseStack();
            snapshot.last().pose().set(call.<PoseStack>getArgument(0).last().pose());
            poses.add(snapshot.last());
            geometry.add(call.getArgument(2));
            return null;
        }).when(collector).submitCustomGeometry(any(), any(), any());

        renderer.submit(state, new PoseStack(), collector, new CameraRenderState());

        List<Vector3f> vertices = new ArrayList<>();
        VertexConsumer consumer = mock(VertexConsumer.class, RETURNS_SELF);
        doAnswer(call -> {
            PoseStack.Pose pose = call.getArgument(0);
            vertices.add(pose.pose().transformPosition(new Vector3f(call.<Float>getArgument(1),
                    call.<Float>getArgument(2), call.<Float>getArgument(3))));
            return consumer;
        }).when(consumer).addVertex(any(PoseStack.Pose.class), anyFloat(), anyFloat(), anyFloat());
        for (int i = 0; i < geometry.size(); i++) {
            geometry.get(i).render(poses.get(i), consumer);
        }
        return vertices;
    }

    private static EntityRendererProvider.Context context() {
        EntityRendererProvider.Context context = mock(EntityRendererProvider.Context.class);
        when(context.getItemModelResolver()).thenReturn(mock(ItemModelResolver.class));
        return context;
    }
}
