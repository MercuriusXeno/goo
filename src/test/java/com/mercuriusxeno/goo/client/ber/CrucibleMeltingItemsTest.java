package com.mercuriusxeno.goo.client.ber;

import com.mercuriusxeno.goo.block.crucible.CrucibleBasin;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.client.RecordingVertexConsumer;
import com.mercuriusxeno.goo.client.RenderContext;
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
import net.minecraft.world.phys.AABB;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The dissolving head's tile path: the item submits once per tile of its image on the
 * dissolve render type, each tile carrying the dissolve fraction, and the tiles together
 * draw the whole item at the basin's center (decision tiles-of-the-items-image).
 */
class CrucibleMeltingItemsTest {

    private static final Identifier ITEM_ATLAS = Identifier.withDefaultNamespace("textures/atlas/items.png");
    private static final int LIGHT = 0x00F000F0;
    private static final float FRACTION = 0.25f;
    private static final float FRONT_Z = 8.5f / 16f;
    private static final AABB GENERATED_ITEM = new AABB(0, 0, 7.5 / 16, 1, 1, FRONT_Z);
    private static final float EPSILON = 1e-5f;

    private static BakedQuad frontFace() {
        TextureAtlasSprite sprite = mock(TextureAtlasSprite.class);
        when(sprite.atlasLocation()).thenReturn(ITEM_ATLAS);
        BakedQuad.MaterialInfo material = new BakedQuad.MaterialInfo(
                sprite, ChunkSectionLayer.CUTOUT, null, -1, true, 0, false);
        return new BakedQuad(new Vector3f(0, 0, FRONT_Z), new Vector3f(0, 1, FRONT_Z),
                new Vector3f(1, 1, FRONT_Z), new Vector3f(1, 0, FRONT_Z), 0L, 0L, 0L, 0L,
                Direction.SOUTH, material);
    }

    /** An item model that submits one full front face, as a generated item's front does. */
    private static ItemStackRenderState oneQuadItem() {
        ItemStackRenderState item = mock(ItemStackRenderState.class);
        when(item.getModelBoundingBox()).thenReturn(GENERATED_ITEM);
        BakedQuad face = frontFace();
        doAnswer(call -> {
            SubmitNodeCollector collector = call.getArgument(1);
            collector.submitItem(call.getArgument(0), ItemDisplayContext.FIXED, call.getArgument(2), 0, 0,
                    new int[0], List.of(face), ItemStackRenderState.FoilType.NONE);
            return null;
        }).when(item).submit(any(PoseStack.class), any(SubmitNodeCollector.class), anyInt(), anyInt(), anyInt());
        return item;
    }

    /**
     * One item quad submits once per tile on the dissolve render type, every vertex
     * carrying the fraction, and the tiles' vertices span the head's square at the center.
     */
    @Test
    void headSubmitsOncePerTileOnTheDissolveType() {
        SubmitNodeCollector delegate = mock(SubmitNodeCollector.class);
        List<RenderType> renderTypes = new ArrayList<>();
        RecordingVertexConsumer recorder = new RecordingVertexConsumer();
        doAnswer(call -> {
            renderTypes.add(call.getArgument(1));
            PoseStack submittedAt = call.getArgument(0);
            SubmitNodeCollector.CustomGeometryRenderer renderer = call.getArgument(2);
            renderer.render(submittedAt.last(), recorder);
            return null;
        }).when(delegate).submitCustomGeometry(any(PoseStack.class), any(RenderType.class), any());

        CrucibleMeltingItems.submitTiles(oneQuadItem(),
                CrucibleItemLayout.headTiles(null, RenderContext.RESTING_RIPPLE_AMPLITUDE, 0f),
                DissolveGlow.single(FRACTION, 0xFFFFFF), new PoseStack(), delegate, LIGHT);

        assertEquals(CrucibleItemLayout.TILE_COUNT, renderTypes.size());
        for (RenderType renderType : renderTypes) {
            assertEquals(GooRenderTypes.crucibleDissolve(ITEM_ATLAS), renderType);
        }
        List<RecordingVertexConsumer.Vertex> vertices = recorder.vertices();
        assertEquals(CrucibleItemLayout.TILE_COUNT * 4, vertices.size());
        float center = (CrucibleBasin.FOOTPRINT_MIN + CrucibleBasin.FOOTPRINT_MAX) / 2f;
        float half = CrucibleItemLayout.HEAD_SIZE / 2f;
        float minX = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float minZ = Float.MAX_VALUE;
        float maxZ = -Float.MAX_VALUE;
        for (RecordingVertexConsumer.Vertex vertex : vertices) {
            assertEquals((int) (DissolveGlow.FRACTION_UNITS * FRACTION), vertex.uv1U());
            assertEquals(vertices.getFirst().y(), vertex.y(), EPSILON);
            minX = Math.min(minX, vertex.x());
            maxX = Math.max(maxX, vertex.x());
            minZ = Math.min(minZ, vertex.z());
            maxZ = Math.max(maxZ, vertex.z());
        }
        assertEquals(center - half, minX, EPSILON);
        assertEquals(center + half, maxX, EPSILON);
        assertEquals(center - half, minZ, EPSILON);
        assertEquals(center + half, maxZ, EPSILON);
    }
}
