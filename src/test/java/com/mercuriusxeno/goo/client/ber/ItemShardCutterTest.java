package com.mercuriusxeno.goo.client.ber;

import com.mercuriusxeno.goo.client.RecordingVertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Breaking an item's face into shards by noise along its pixels: every texel in exactly one
 * shard, each shard drawing only its own texels, edges on texel boundaries and not a grid,
 * and 8 to 14 shards varying by item (decision tiles-of-the-items-image).
 */
class ItemShardCutterTest {

    private static final int TEXELS = 16;
    private static final float EPSILON = 1e-4f;
    private static final float FRONT_Z = 8.5f / 16f;
    private static final Identifier ITEM_ATLAS = Identifier.withDefaultNamespace("textures/atlas/items.png");
    private static final List<String> SAMPLE_ITEMS = List.of("minecraft:iron_ingot", "minecraft:diamond",
            "minecraft:apple", "minecraft:stick", "minecraft:redstone", "minecraft:bone", "goo:gasket",
            "minecraft:ender_pearl", "minecraft:blaze_rod", "minecraft:emerald", "minecraft:coal",
            "minecraft:gold_nugget");

    private static ItemShardCutter.ShardMap cutFor(String itemId) {
        return ItemShardCutter.cut(ItemShardCutter.seedOf(itemId), TEXELS, TEXELS, ItemShardCutter.TexelOpacity.ALL);
    }

    /** The unit front face of a generated item, its UV the texel's own position over the face. */
    private static BakedQuad frontFace() {
        TextureAtlasSprite sprite = mock(TextureAtlasSprite.class);
        when(sprite.atlasLocation()).thenReturn(ITEM_ATLAS);
        BakedQuad.MaterialInfo material = new BakedQuad.MaterialInfo(
                sprite, ChunkSectionLayer.CUTOUT, null, -1, true, 0, false);
        return new BakedQuad(new Vector3f(0, 0, FRONT_Z), new Vector3f(0, 1, FRONT_Z),
                new Vector3f(1, 1, FRONT_Z), new Vector3f(1, 0, FRONT_Z),
                UVPair.pack(0f, 1f), UVPair.pack(0f, 0f), UVPair.pack(1f, 0f), UVPair.pack(1f, 1f),
                Direction.SOUTH, material);
    }

    /** Emits one shard of the unit front face and records its vertices in the face's space. */
    private static List<RecordingVertexConsumer.Vertex> emitShard(ShardFace face, int shard) {
        SubmitNodeCollector delegate = mock(SubmitNodeCollector.class);
        new DissolvingItemCollector(delegate, DissolveGlow.single(0f, 0xFFFFFF), face, shard, new Matrix4f())
                .submitItem(new PoseStack(), ItemDisplayContext.FIXED, 0, 0, 0, new int[0], List.of(frontFace()),
                        ItemStackRenderState.FoilType.NONE);
        ArgumentCaptor<SubmitNodeCollector.CustomGeometryRenderer> geometry =
                ArgumentCaptor.forClass(SubmitNodeCollector.CustomGeometryRenderer.class);
        verify(delegate, atLeast(0)).submitCustomGeometry(any(PoseStack.class), any(), geometry.capture());
        RecordingVertexConsumer recorder = new RecordingVertexConsumer();
        for (SubmitNodeCollector.CustomGeometryRenderer renderer : geometry.getAllValues()) {
            renderer.render(new PoseStack().last(), recorder);
        }
        return recorder.vertices();
    }

    private static ShardFace unitFace(ItemShardCutter.ShardMap map) {
        return new ShardFace(new QuadRectClipper.Rect(0f, 0f, 1f, 1f), map);
    }

    @Nested
    class Coverage {

        /** Every texel belongs to exactly one shard, and every shard holds a texel. */
        @Test
        void everyTexelInExactlyOneShard() {
            ItemShardCutter.ShardMap map = cutFor("minecraft:iron_ingot");

            int[] texelsPerShard = new int[map.count()];
            for (int row = 0; row < TEXELS; row++) {
                for (int column = 0; column < TEXELS; column++) {
                    int owner = map.ownerOf(column, row);
                    assertTrue(owner >= 0 && owner < map.count(), "texel " + column + "," + row + " owns " + owner);
                    texelsPerShard[owner]++;
                }
            }
            int total = 0;
            for (int texels : texelsPerShard) {
                assertTrue(texels > 0, "an empty shard");
                total += texels;
            }
            assertEquals(TEXELS * TEXELS, total);
        }

        /**
         * Each shard's emitted face quads cover exactly its own texels: every quad's UV
         * rectangle lies on texels the shard owns, and the shard's quads' areas sum to its texels.
         */
        @Test
        void eachShardDrawsOnlyItsOwnTexels() {
            ItemShardCutter.ShardMap map = cutFor("minecraft:diamond");
            ShardFace face = unitFace(map);
            float texelArea = 1f / (TEXELS * TEXELS);
            float drawn = 0f;
            for (int shard = 0; shard < map.count(); shard++) {
                List<RecordingVertexConsumer.Vertex> vertices = emitShard(face, shard);
                int owned = 0;
                for (int i = 0; i < TEXELS * TEXELS; i++) {
                    owned += map.owners()[i] == shard ? 1 : 0;
                }
                float area = 0f;
                for (int quad = 0; quad < vertices.size(); quad += 4) {
                    List<RecordingVertexConsumer.Vertex> corners = vertices.subList(quad, quad + 4);
                    float minU = (float) corners.stream().mapToDouble(RecordingVertexConsumer.Vertex::u).min().orElseThrow();
                    float maxU = (float) corners.stream().mapToDouble(RecordingVertexConsumer.Vertex::u).max().orElseThrow();
                    float minV = (float) corners.stream().mapToDouble(RecordingVertexConsumer.Vertex::v).min().orElseThrow();
                    float maxV = (float) corners.stream().mapToDouble(RecordingVertexConsumer.Vertex::v).max().orElseThrow();
                    int row = TEXELS - 1 - (int) Math.floor(minV * TEXELS + EPSILON);
                    for (int column = Math.round(minU * TEXELS); column < Math.round(maxU * TEXELS); column++) {
                        assertEquals(shard, map.ownerOf(column, row), "shard " + shard + " draws texel " + column
                                + "," + row);
                    }
                    assertEquals(1f / TEXELS, maxV - minV, EPSILON, "a run spans one texel row");
                    area += (maxU - minU) * (maxV - minV);
                }
                assertEquals(owned * texelArea, area, EPSILON, "shard " + shard);
                drawn += area;
            }
            assertEquals(1f, drawn, EPSILON);
        }
    }

    @Nested
    class Shape {

        /** Every emitted vertex of every shard lies on a texel boundary of the face. */
        @Test
        void shardEdgesLieOnTexelBoundaries() {
            for (String itemId : SAMPLE_ITEMS.subList(0, 4)) {
                ItemShardCutter.ShardMap map = cutFor(itemId);
                for (int shard = 0; shard < map.count(); shard++) {
                    for (RecordingVertexConsumer.Vertex vertex : emitShard(unitFace(map), shard)) {
                        float column = vertex.x() * TEXELS;
                        float row = vertex.y() * TEXELS;
                        assertEquals(Math.round(column), column, EPSILON, itemId + " vertex off a texel edge");
                        assertEquals(Math.round(row), row, EPSILON, itemId + " vertex off a texel edge");
                    }
                }
            }
        }

        /** Every sampled item breaks into at least one shard that is not an axis-aligned rectangle. */
        @Test
        void noiseBreaksShardsOutOfRectangles() {
            for (String itemId : SAMPLE_ITEMS) {
                ItemShardCutter.ShardMap map = cutFor(itemId);
                boolean jagged = false;
                for (int shard = 0; shard < map.count() && !jagged; shard++) {
                    jagged = !isRectangle(map, shard);
                }
                assertTrue(jagged, itemId + " broke into rectangles only");
            }
        }

        private static boolean isRectangle(ItemShardCutter.ShardMap map, int shard) {
            int minColumn = TEXELS;
            int maxColumn = -1;
            int minRow = TEXELS;
            int maxRow = -1;
            int texels = 0;
            for (int row = 0; row < TEXELS; row++) {
                for (int column = 0; column < TEXELS; column++) {
                    if (map.ownerOf(column, row) == shard) {
                        minColumn = Math.min(minColumn, column);
                        maxColumn = Math.max(maxColumn, column);
                        minRow = Math.min(minRow, row);
                        maxRow = Math.max(maxRow, row);
                        texels++;
                    }
                }
            }
            return texels == (maxColumn - minColumn + 1) * (maxRow - minRow + 1);
        }
    }

    @Nested
    class Count {

        /** Every sampled item breaks into 8 to 14 shards, and the count varies across items. */
        @Test
        void shardCountStaysInRangeAndVaries() {
            assertEquals(8, ItemShardCutter.MIN_SHARDS);
            assertEquals(14, ItemShardCutter.MAX_SHARDS);
            Set<Integer> counts = new HashSet<>();
            for (String itemId : SAMPLE_ITEMS) {
                int count = cutFor(itemId).count();
                assertTrue(count >= ItemShardCutter.MIN_SHARDS && count <= ItemShardCutter.MAX_SHARDS,
                        itemId + " broke into " + count);
                counts.add(count);
            }
            assertTrue(counts.size() >= 2, "every item broke into " + counts);
        }

        /** Points land only on opaque texels, so a small opaque patch still holds every shard's seed. */
        @Test
        void pointsLandOnOpaqueTexels() {
            ItemShardCutter.ShardMap map = ItemShardCutter.cut(ItemShardCutter.seedOf("minecraft:stick"),
                    TEXELS, TEXELS, (column, row) -> column >= 4 && column < 12 && row >= 4 && row < 12);

            Set<Integer> inPatch = new HashSet<>();
            for (int row = 4; row < 12; row++) {
                for (int column = 4; column < 12; column++) {
                    inPatch.add(map.ownerOf(column, row));
                }
            }
            assertFalse(inPatch.isEmpty());
            assertTrue(inPatch.size() >= ItemShardCutter.MIN_SHARDS - 2,
                    "only " + inPatch.size() + " shards reach the opaque patch of " + map.count());
        }
    }
}
