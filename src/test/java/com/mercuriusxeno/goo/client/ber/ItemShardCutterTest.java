package com.mercuriusxeno.goo.client.ber;

import com.mercuriusxeno.goo.client.RecordingVertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Breaking an item's model into shards by noise along its pixels: every cell in exactly one
 * shard, each shard drawing only its own cells, edges on texel boundaries and not a grid on a
 * flat item and on every face of a block, and 8 to 14 shards varying by item (decision
 * tiles-of-the-items-image).
 */
class ItemShardCutterTest {

    private static final int TEXELS = ShardModels.TEXELS;
    private static final float EPSILON = 1e-4f;
    private static final List<String> SAMPLE_ITEMS = List.of("minecraft:iron_ingot", "minecraft:diamond",
            "minecraft:apple", "minecraft:stick", "minecraft:redstone", "minecraft:bone", "goo:gasket",
            "minecraft:ender_pearl", "minecraft:blaze_rod", "minecraft:emerald", "minecraft:coal",
            "minecraft:gold_nugget");

    /** Emits one shard of a model's quads at an identity pose and records its vertices. */
    private static List<RecordingVertexConsumer.Vertex> emitShard(ShardedModel model, List<BakedQuad> quads,
                                                                  int shard) {
        SubmitNodeCollector delegate = mock(SubmitNodeCollector.class);
        new DissolvingItemCollector(delegate, DissolveGlow.single(0f, 0xFFFFFF), model, shard, new Matrix4f())
                .submitItem(new PoseStack(), ItemDisplayContext.FIXED, 0, 0, 0, new int[0], quads,
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

    /** The area of one emitted quad, from its first three corners. */
    private static float quadArea(List<RecordingVertexConsumer.Vertex> corners) {
        float[] a = {corners.get(1).x() - corners.get(0).x(), corners.get(1).y() - corners.get(0).y(),
            corners.get(1).z() - corners.get(0).z()};
        float[] b = {corners.get(3).x() - corners.get(0).x(), corners.get(3).y() - corners.get(0).y(),
            corners.get(3).z() - corners.get(0).z()};
        float cx = a[1] * b[2] - a[2] * b[1];
        float cy = a[2] * b[0] - a[0] * b[2];
        float cz = a[0] * b[1] - a[1] * b[0];
        return (float) Math.sqrt(cx * cx + cy * cy + cz * cz);
    }

    /** The front face's owners as a texel map, row by row from the bottom. */
    private static int[] frontOwners(ShardedModel model, List<BakedQuad> quads) {
        ShardedModel.QuadCut cut = model.cutOf(quads.getFirst());
        assertNotNull(cut);
        return cut.owners();
    }

    private static boolean isRectangle(int[] owners, int shard) {
        int minColumn = TEXELS;
        int maxColumn = -1;
        int minRow = TEXELS;
        int maxRow = -1;
        int cells = 0;
        for (int i = 0; i < owners.length; i++) {
            if (owners[i] == shard) {
                minColumn = Math.min(minColumn, i % TEXELS);
                maxColumn = Math.max(maxColumn, i % TEXELS);
                minRow = Math.min(minRow, i / TEXELS);
                maxRow = Math.max(maxRow, i / TEXELS);
                cells++;
            }
        }
        return cells == (maxColumn - minColumn + 1) * (maxRow - minRow + 1);
    }

    @Nested
    class Coverage {

        /**
         * Every cell of every quad belongs to exactly one shard, each shard holds a cell, and
         * the shards' emitted pieces together cover every quad's area once.
         */
        @Test
        void everyCellInExactlyOneShardAndDrawnOnce() {
            List<BakedQuad> quads = ShardModels.flatItem();
            ShardedModel model = ShardModels.flatFor("minecraft:iron_ingot");
            int[] cellsPerShard = new int[model.count()];
            float modelArea = 0f;
            for (BakedQuad quad : quads) {
                ShardedModel.QuadCut cut = model.cutOf(quad);
                assertNotNull(cut);
                for (int owner : cut.owners()) {
                    assertTrue(owner >= 0 && owner < model.count(), "a cell owned by " + owner);
                    cellsPerShard[owner]++;
                }
                modelArea += quadArea(QuadRectClipper.verticesOf(quad).stream()
                        .map(v -> new RecordingVertexConsumer.Vertex(v.x(), v.y(), v.z(), 0, 0, 0, 0, 0, 0, 0, 0, 0, 0))
                        .toList());
            }
            for (int cells : cellsPerShard) {
                assertTrue(cells > 0, "an empty shard");
            }
            float drawn = 0f;
            for (int shard = 0; shard < model.count(); shard++) {
                List<RecordingVertexConsumer.Vertex> vertices = emitShard(model, quads, shard);
                for (int quad = 0; quad < vertices.size(); quad += 4) {
                    drawn += quadArea(vertices.subList(quad, quad + 4));
                }
            }
            assertEquals(modelArea, drawn, EPSILON);
        }

        /** A shard's pieces of the front face lie on exactly the texels it owns there, UVs with them. */
        @Test
        void eachShardDrawsOnlyItsOwnTexels() {
            List<BakedQuad> quads = ShardModels.flatItem();
            ShardedModel model = ShardModels.flatFor("minecraft:diamond");
            int[] owners = frontOwners(model, quads);
            for (int shard = 0; shard < model.count(); shard++) {
                List<RecordingVertexConsumer.Vertex> vertices = emitShard(model, List.of(quads.getFirst()), shard);
                int owned = 0;
                for (int owner : owners) {
                    owned += owner == shard ? 1 : 0;
                }
                float area = 0f;
                for (int quad = 0; quad < vertices.size(); quad += 4) {
                    List<RecordingVertexConsumer.Vertex> corners = vertices.subList(quad, quad + 4);
                    double centerX = corners.stream().mapToDouble(RecordingVertexConsumer.Vertex::x).average().orElseThrow();
                    double centerY = corners.stream().mapToDouble(RecordingVertexConsumer.Vertex::y).average().orElseThrow();
                    double centerU = corners.stream().mapToDouble(RecordingVertexConsumer.Vertex::u).average().orElseThrow();
                    int row = (int) Math.floor(centerY * TEXELS);
                    int column = (int) Math.floor(centerX * TEXELS);
                    assertEquals(shard, owners[row * TEXELS + column], "shard " + shard + " draws a texel it lacks");
                    assertEquals(centerX, centerU, EPSILON, "a piece's UV strays from its texels");
                    area += quadArea(corners);
                }
                assertEquals(owned / (float) (TEXELS * TEXELS), area, EPSILON, "shard " + shard);
            }
        }
    }

    @Nested
    class Shape {

        /** Every emitted vertex of every shard of a flat item lies on a texel boundary of its face. */
        @Test
        void flatShardEdgesLieOnTexelBoundaries() {
            List<BakedQuad> quads = ShardModels.flatItem();
            for (String itemId : SAMPLE_ITEMS.subList(0, 4)) {
                ShardedModel model = ShardModels.flatFor(itemId);
                for (int shard = 0; shard < model.count(); shard++) {
                    for (RecordingVertexConsumer.Vertex vertex : emitShard(model, quads, shard)) {
                        assertEquals(Math.round(vertex.x() * TEXELS), vertex.x() * TEXELS, EPSILON, itemId);
                        assertEquals(Math.round(vertex.y() * TEXELS), vertex.y() * TEXELS, EPSILON, itemId);
                    }
                }
            }
        }

        /** Every sampled item's face breaks into at least one shard that is not a rectangle. */
        @Test
        void noiseBreaksShardsOutOfRectangles() {
            List<BakedQuad> quads = ShardModels.flatItem();
            for (String itemId : SAMPLE_ITEMS) {
                ShardedModel model = ShardModels.flatFor(itemId);
                int[] owners = frontOwners(model, quads);
                boolean jagged = false;
                for (int shard = 0; shard < model.count() && !jagged; shard++) {
                    jagged = !isRectangle(owners, shard);
                }
                assertTrue(jagged, itemId + " broke into rectangles only");
            }
        }

        /** A flat item's back and edge cells break with the front texel they sit behind. */
        @Test
        void flatBackAndEdgesFollowTheFront() {
            List<BakedQuad> quads = ShardModels.flatItem();
            ShardedModel model = ShardModels.flatFor("minecraft:apple");
            int[] front = frontOwners(model, quads);
            ShardedModel.QuadCut back = model.cutOf(quads.get(1));
            assertNotNull(back);
            for (int i = 0; i < front.length; i++) {
                assertEquals(front[i], back.owners()[i], "back texel " + i);
            }
            ShardedModel.QuadCut westStrip = model.cutOf(quads.get(2));
            assertNotNull(westStrip);
            assertEquals(front[0], westStrip.owners()[0], "the edge strip beside texel 0,0");
        }

        /**
         * A cube breaks across all six faces: every face's cells spread over at least two
         * shards, and every emitted vertex lies on a texel boundary of its face.
         */
        @Test
        void cubeBreaksAcrossEveryFace() {
            List<BakedQuad> quads = ShardModels.cube();
            ShardedModel model = ShardModels.cubeFor("minecraft:oak_log");
            for (BakedQuad quad : quads) {
                ShardedModel.QuadCut cut = model.cutOf(quad);
                assertNotNull(cut);
                assertEquals(TEXELS * TEXELS, cut.owners().length, quad.direction() + " cut into texel cells");
                Set<Integer> shards = new HashSet<>();
                for (int owner : cut.owners()) {
                    shards.add(owner);
                }
                assertTrue(shards.size() >= 2, quad.direction() + " rides whole with one shard");
            }
            for (int shard = 0; shard < model.count(); shard++) {
                for (RecordingVertexConsumer.Vertex vertex : emitShard(model, quads, shard)) {
                    for (float coordinate : new float[] {vertex.x(), vertex.y(), vertex.z()}) {
                        assertEquals(Math.round(coordinate * TEXELS), coordinate * TEXELS, EPSILON,
                                "a cube vertex off a texel edge");
                    }
                }
            }
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
                int count = ShardModels.flatFor(itemId).count();
                assertTrue(count >= ItemShardCutter.MIN_SHARDS && count <= ItemShardCutter.MAX_SHARDS,
                        itemId + " broke into " + count);
                counts.add(count);
            }
            assertTrue(counts.size() >= 2, "every item broke into " + counts);
        }

        /** Points land only on opaque cells, and transparent cells join no shard. */
        @Test
        void transparentCellsJoinNoShard() {
            float[][] centers = new float[TEXELS * TEXELS][];
            boolean[] opaque = new boolean[centers.length];
            for (int i = 0; i < centers.length; i++) {
                int column = i % TEXELS;
                int row = i / TEXELS;
                centers[i] = new float[] {column + 0.5f, row + 0.5f, 0f};
                opaque[i] = column >= 4 && column < 12 && row >= 4 && row < 12;
            }

            ItemShardCutter.Assignment assignment =
                    ItemShardCutter.assign(ItemShardCutter.seedOf("minecraft:stick"), centers, opaque, 1f);

            for (int i = 0; i < centers.length; i++) {
                assertEquals(opaque[i], assignment.owners()[i] != ItemShardCutter.NO_SHARD, "cell " + i);
            }
            assertTrue(assignment.count() >= ItemShardCutter.MIN_SHARDS);
        }
    }
}
