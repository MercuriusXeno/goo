package com.mercuriusxeno.goo.client.ber;

import com.mercuriusxeno.goo.block.crucible.CrucibleBasin;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.client.RecordingVertexConsumer;
import com.mercuriusxeno.goo.client.RenderContext;
import com.mercuriusxeno.goo.client.SurfaceRipple;
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
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
     * A real item render state whose one layer carries a local transform, centering and
     * turning its model the way a generated item's layer does, with the front face's
     * corners as its extents.
     */
    private static ItemStackRenderState transformedLayerItem() {
        ItemStackRenderState item = new ItemStackRenderState();
        ItemStackRenderState.LayerRenderState layer = item.newLayer();
        layer.prepareQuadList().add(frontFace());
        layer.setLocalTransform(new Matrix4f().rotateY((float) Math.PI).translate(-0.5f, -0.5f, -0.5f));
        Vector3fc[] corners = {new Vector3f(0, 0, FRONT_Z), new Vector3f(1, 1, FRONT_Z)};
        layer.setExtents(() -> corners);
        return item;
    }

    private static final long SEED = ItemShardCutter.seedOf("minecraft:iron_ingot");

    /** What one head submission drew: the render type of each submission and every vertex. */
    private record Submitted(List<RenderType> renderTypes, List<RecordingVertexConsumer.Vertex> vertices,
                             ShardFace face) {
    }

    /** Submits the head's shards at home and records every vertex they emit in the block's space. */
    private static Submitted submitAndRecord(ItemStackRenderState item) {
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
        ShardFace face = ItemFaceProbe.probe(item, SEED);
        CrucibleMeltingItems.submitShards(item, face,
                CrucibleItemLayout.shardHomes(null, RenderContext.RESTING_RIPPLE_AMPLITUDE,
                        new SurfaceRipple.Field(0, 0, 0f), CrucibleMeltingItems.homeOffsets(face)),
                DissolveGlow.single(FRACTION, 0xFFFFFF), new PoseStack(), delegate, LIGHT);
        return new Submitted(renderTypes, recorder.vertices(), face);
    }

    /**
     * Asserts the shards at home draw the whole item flat at the basin center: every vertex
     * in one plane, the vertices spanning the head's square, the quads' areas summing to it.
     */
    private static void assertWholeAndFlat(List<RecordingVertexConsumer.Vertex> vertices) {
        float center = (CrucibleBasin.FOOTPRINT_MIN + CrucibleBasin.FOOTPRINT_MAX) / 2f;
        float half = CrucibleItemLayout.HEAD_SIZE / 2f;
        float minX = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float minZ = Float.MAX_VALUE;
        float maxZ = -Float.MAX_VALUE;
        float area = 0f;
        for (int quad = 0; quad < vertices.size(); quad += 4) {
            float twice = 0f;
            for (int corner = 0; corner < 4; corner++) {
                RecordingVertexConsumer.Vertex a = vertices.get(quad + corner);
                RecordingVertexConsumer.Vertex b = vertices.get(quad + (corner + 1) % 4);
                assertEquals(vertices.getFirst().y(), a.y(), EPSILON, "a shard stands off the face's plane");
                twice += a.x() * b.z() - b.x() * a.z();
                minX = Math.min(minX, a.x());
                maxX = Math.max(maxX, a.x());
                minZ = Math.min(minZ, a.z());
                maxZ = Math.max(maxZ, a.z());
            }
            area += Math.abs(twice) / 2f;
        }
        assertEquals(center - half, minX, EPSILON);
        assertEquals(center + half, maxX, EPSILON);
        assertEquals(center - half, minZ, EPSILON);
        assertEquals(center + half, maxZ, EPSILON);
        assertEquals(CrucibleItemLayout.HEAD_SIZE * CrucibleItemLayout.HEAD_SIZE, area, EPSILON);
    }

    /**
     * An item whose layer moves its model before submitting still draws whole and flat:
     * the shards cut the face in the space the bounding box measures, so every shard carries
     * its own piece of the face, lying face up, together spanning the head's square.
     */
    @Test
    void layerTransformedItemTilesWholeAndFlat() {
        assertWholeAndFlat(submitAndRecord(transformedLayerItem()).vertices());
    }

    /**
     * One item quad submits once per shard of the item's shard map on the dissolve render
     * type, every vertex carrying the fraction, the shards together the whole item at home.
     */
    @Test
    void headSubmitsOncePerShardOnTheDissolveType() {
        Submitted submitted = submitAndRecord(oneQuadItem());

        assertEquals(submitted.face().map().count(), submitted.renderTypes().size());
        assertTrue(submitted.face().map().count() >= ItemShardCutter.MIN_SHARDS);
        for (RenderType renderType : submitted.renderTypes()) {
            assertEquals(GooRenderTypes.crucibleDissolve(ITEM_ATLAS), renderType);
        }
        for (RecordingVertexConsumer.Vertex vertex : submitted.vertices()) {
            assertEquals((int) (DissolveGlow.FRACTION_UNITS * FRACTION), vertex.uv1U());
        }
        assertWholeAndFlat(submitted.vertices());
    }
}
