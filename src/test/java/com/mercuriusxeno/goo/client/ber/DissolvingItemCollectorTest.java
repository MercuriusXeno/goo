package com.mercuriusxeno.goo.client.ber;

import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.client.RecordingVertexConsumer;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The dissolving item's quads re-emit on the dissolve render type, each vertex carrying
 * the dissolve fraction and glow color, and the dissolve pipeline registers with its own
 * shaders (decision dissolve-shader-on-item).
 */
class DissolvingItemCollectorTest {

    private static final Identifier ITEM_ATLAS = Identifier.withDefaultNamespace("textures/atlas/items.png");
    private static final int LIGHT = 0x00F000F0;

    private static BakedQuad quadOnItemAtlas() {
        TextureAtlasSprite sprite = mock(TextureAtlasSprite.class);
        when(sprite.atlasLocation()).thenReturn(ITEM_ATLAS);
        BakedQuad.MaterialInfo material = new BakedQuad.MaterialInfo(
                sprite, ChunkSectionLayer.CUTOUT, null, -1, true, 0, false);
        return new BakedQuad(new Vector3f(0, 0, 0), new Vector3f(0, 1, 0), new Vector3f(1, 1, 0),
                new Vector3f(1, 0, 0), 0L, 0L, 0L, 0L, Direction.SOUTH, material);
    }

    /**
     * Submits one quad through the collector and records what it re-emits.
     *
     * @param glow       the dissolve the collector carries
     * @param renderType receives the render type the quad was re-emitted on
     * @return the recorded vertices
     */
    private static List<RecordingVertexConsumer.Vertex> submitThrough(DissolveGlow glow,
                                                                      RenderType[] renderType) {
        SubmitNodeCollector delegate = mock(SubmitNodeCollector.class);
        new DissolvingItemCollector(delegate, glow).submitItem(new PoseStack(), ItemDisplayContext.FIXED,
                LIGHT, 0, 0, new int[0], List.of(quadOnItemAtlas()), ItemStackRenderState.FoilType.NONE);

        ArgumentCaptor<RenderType> type = ArgumentCaptor.forClass(RenderType.class);
        ArgumentCaptor<SubmitNodeCollector.CustomGeometryRenderer> geometry =
                ArgumentCaptor.forClass(SubmitNodeCollector.CustomGeometryRenderer.class);
        verify(delegate).submitCustomGeometry(any(PoseStack.class), type.capture(), geometry.capture());
        renderType[0] = type.getValue();
        RecordingVertexConsumer recorder = new RecordingVertexConsumer();
        geometry.getValue().render(new PoseStack().last(), recorder);
        return recorder.vertices();
    }

    /** A quarter-dissolved item glowing orange re-emits on the dissolve type with both in every vertex. */
    @Test
    void quadsCarryTheDissolveOnTheDissolveType() {
        DissolveGlow glow = new DissolveGlow(0.25f, 0xFF8000);
        RenderType[] renderType = new RenderType[1];

        List<RecordingVertexConsumer.Vertex> vertices = submitThrough(glow, renderType);

        assertEquals(GooRenderTypes.crucibleDissolve(ITEM_ATLAS), renderType[0]);
        assertEquals(4, vertices.size());
        for (RecordingVertexConsumer.Vertex vertex : vertices) {
            assertEquals(DissolveGlow.FRACTION_UNITS / 4, vertex.uv1U());
            assertEquals(0b11111_100000_00000, vertex.uv1V());
            assertEquals(List.of(0xF0, 0xF0), List.of(vertex.uv2U(), vertex.uv2V()));
        }
    }

    /** The glow color packs to RGB565 and the fraction clamps to a whole item. */
    @Test
    void glowPacksIntoTheOverlayCoords() {
        DissolveGlow white = new DissolveGlow(1.5f, 0xFFFFFF);

        assertEquals(DissolveGlow.FRACTION_UNITS, white.fractionUnits());
        assertEquals(0xFFFF, white.glowRgb565());
        assertEquals(DissolveGlow.FRACTION_UNITS | 0xFFFF << 16, white.overlayCoords());
        assertEquals(0, new DissolveGlow(-1f, 0).overlayCoords());
    }

    /** The dissolve pipeline draws with its own vertex and fragment shaders and registers with the rest. */
    @Test
    void dissolvePipelineRegistersWithItsShaders() {
        RenderPipeline pipeline = GooRenderTypes.CRUCIBLE_DISSOLVE;
        RegisterRenderPipelinesEvent event = mock(RegisterRenderPipelinesEvent.class);

        GooRenderTypes.registerPipelines(event);

        assertEquals(Identifier.fromNamespaceAndPath("goo", "core/crucible_dissolve"), pipeline.getFragmentShader());
        assertEquals(Identifier.fromNamespaceAndPath("goo", "core/crucible_dissolve"), pipeline.getVertexShader());
        assertFalse(pipeline.getSamplers().isEmpty());
        verify(event).registerPipeline(pipeline);
    }
}
