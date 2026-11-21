package com.mercuriusxeno.goo.client.model;

import com.mercuriusxeno.goo.client.ClientGooTypes;
import com.mercuriusxeno.goo.client.GooRenderUtil;
import com.mercuriusxeno.goo.client.GooSubmitter;
import com.mercuriusxeno.goo.client.RecordingVertexConsumer;
import com.mercuriusxeno.goo.client.ability.SprayingClient;
import com.mercuriusxeno.goo.item.GooItem;
import com.mercuriusxeno.goo.type.GooTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.QuadCollection;
import net.minecraft.resources.Identifier;
import org.joml.Quaternionf;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Raw goo held in a hand draws through the glove's renderer as the glove without its model part: the palm cube a held
 * glove draws, at the glove's size and on the glove's palm, and no glove body. GooItemModelTest covers the routing of
 * held goo to this renderer.
 * decision raw-goo-items-work-bare-handed-for-the-held-type
 */
class HeldRawGooRenderTest {

    @Test
    void heldRawGooDrawsTheGlovesPalmCubeAtItsSizeAndPlace() {
        List<RecordingVertexConsumer.Vertex> glove = palmCubeDrawn(new GloveSpecialRenderer.GloveData(null,
                GooTypes.ASTRAL, 0f, 1f, false, false));
        List<RecordingVertexConsumer.Vertex> goo = palmCubeDrawn(new GloveSpecialRenderer.GloveData(
                mock(GooItem.class), GooTypes.ASTRAL, 0f, 1f, false, false));

        assertFalse(glove.isEmpty(), "the held glove should draw its palm cube");
        assertEquals(glove, goo, "held raw goo should draw the glove's palm cube, core and shell, vertex for vertex");
    }

    /**
     * The bare arm draws with the solid geometry, before any translucent face, as the glove body does: on a translucent
     * type the pulsing lifted blob sorted ahead of the sleeve every other frame and hid it
     * (decision diagnose-and-correct-the-palm-core-cull).
     */
    @Test
    void theBareArmSubmitsOnTheCutoutSkinType() {
        Identifier skin = Identifier.withDefaultNamespace("textures/entity/player/wide/steve.png");

        assertEquals(GooSubmitter.cutoutOn(skin), GloveSpecialRenderer.bareArmType(skin, false));
    }

    @Test
    void heldRawGooSubmitsItsPalmBlobAndNoGloveBody() {
        List<RenderType> types = submittedTypes(new GloveSpecialRenderer.GloveData(mock(GooItem.class),
                GooTypes.ASTRAL, 0f, 1f, false, false));

        assertFalse(types.contains(GloveSpecialRenderer.bodyType()), "held raw goo should draw no glove body");
        assertTrue(types.contains(GloveSpecialRenderer.coreType()), "held raw goo should draw its palm blob");
    }

    private static List<RenderType> submittedTypes(GloveSpecialRenderer.GloveData data) {
        List<RenderType> types = new ArrayList<>();
        SubmitNodeCollector collector = mock(SubmitNodeCollector.class);
        doAnswer(call -> {
            types.add(call.getArgument(1));
            return null;
        }).when(collector).submitCustomGeometry(any(), any(), any());
        submit(data, new PoseStack(), collector);
        return types;
    }

    /** Every vertex the palm cube's core and shell emit, in a hand turned some way. */
    private static List<RecordingVertexConsumer.Vertex> palmCubeDrawn(GloveSpecialRenderer.GloveData data) {
        PoseStack hand = new PoseStack();
        hand.rotate(new Quaternionf().rotationXYZ(1.2f, 0.4f, -0.3f));
        RecordingVertexConsumer drawn = new RecordingVertexConsumer();
        SubmitNodeCollector collector = mock(SubmitNodeCollector.class);
        doAnswer(call -> {
            if (call.getArgument(1) == GloveSpecialRenderer.coreType()) {
                call.<SubmitNodeCollector.CustomGeometryRenderer>getArgument(2)
                        .render(call.<PoseStack>getArgument(0).last(), drawn);
            }
            return null;
        }).when(collector).submitCustomGeometry(any(), any(), any());
        try (MockedStatic<GooRenderUtil> util = mockStatic(GooRenderUtil.class, CALLS_REAL_METHODS);
             MockedStatic<ClientGooTypes> colors = mockStatic(ClientGooTypes.class)) {
            util.when(() -> GooRenderUtil.lookupFluidSprite(any())).thenReturn(mock(TextureAtlasSprite.class));
            submit(data, hand, collector);
        }
        return drawn.vertices();
    }

    /** Submits the data with a client standing and every glove body empty, as GloveSpecialRendererTest does. */
    private static void submit(GloveSpecialRenderer.GloveData data, PoseStack pose, SubmitNodeCollector collector) {
        QuadCollection body = mock(QuadCollection.class);
        when(body.getAll()).thenReturn(List.of());
        try (SprayingClient client = SprayingClient.another(GooTypes.ASTRAL);
             MockedStatic<GloveBodyModels> models = mockStatic(GloveBodyModels.class)) {
            models.when(() -> GloveBodyModels.getModel(any())).thenReturn(body);
            new GloveSpecialRenderer().submit(data, pose, collector, 0, 0, false, 0);
        }
    }
}
