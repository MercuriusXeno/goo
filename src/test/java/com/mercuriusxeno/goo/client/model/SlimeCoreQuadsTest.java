package com.mercuriusxeno.goo.client.model;

import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;
import org.joml.Vector3fc;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Covers the quads a slime core's boxes draw as: an opaque body on the
 * cell's goo type's fluid sprite, untinted as the palm blob's core is,
 * spanning its boxes, drawing nothing on a face its block shares with a
 * joined core and textured where it stands in its block so the texture runs
 * on across a join. The sprites stand in for weird's and kinetic's.
 */
class SlimeCoreQuadsTest {

    private static final float FIFTEENTH = 1f / 15f;
    private static final float EPSILON = 1e-5f;
    private static final int WHOLE_LAYERS = 15;
    private static final long SWEEP_SEED = 7L;
    private static final int SWEEP_TRIALS = 2000;

    private static BakedQuad.MaterialInfo coreMaterial(TextureAtlasSprite fluid) {
        return new BakedQuad.MaterialInfo(fluid, ChunkSectionLayer.SOLID, null, SlimeCoreQuads.NO_TINT, true, 0, false);
    }

    /**
     * A sprite spanning the whole texture space, so a quad's coordinates read as the sprite's own.
     */
    private static TextureAtlasSprite wholeSprite() {
        TextureAtlasSprite sprite = mock(TextureAtlasSprite.class);
        when(sprite.getU(anyFloat())).thenAnswer(call -> call.getArgument(0));
        when(sprite.getV(anyFloat())).thenAnswer(call -> call.getArgument(0));
        return sprite;
    }

    private static List<BakedQuad> loneCore(int layers, TextureAtlasSprite fluid) {
        return SlimeCoreQuads.quadsOf(LayersAround.alone(layers), coreMaterial(fluid));
    }

    @Nested
    class Box {

        @Test
        void slimeAtEightLayersDrawsWeirdsFluidOverTheRulesBox() {
            TextureAtlasSprite weird = mock(TextureAtlasSprite.class);
            List<BakedQuad> quads = loneCore(8, weird);
            assertSpans(quads, new SlimeCoreBox(2, 2, 2, 13, 6, 13));
            assertTrue(quads.stream().allMatch(quad -> quad.materialInfo().sprite() == weird));
            assertTrue(quads.stream().noneMatch(quad -> quad.materialInfo().isTinted()));
        }

        @Test
        void slickAtEightLayersDrawsKineticsFluidOverTheRulesBox() {
            TextureAtlasSprite kinetic = mock(TextureAtlasSprite.class);
            List<BakedQuad> quads = loneCore(8, kinetic);
            assertSpans(quads, new SlimeCoreBox(2, 2, 2, 13, 6, 13));
            assertTrue(quads.stream().allMatch(quad -> quad.materialInfo().sprite() == kinetic));
        }

        @Test
        void aCellAtFourLayersDrawsNoCore() {
            assertEquals(List.of(), loneCore(4, mock(TextureAtlasSprite.class)));
        }

        @Test
        void theCoreDrawsOnTheSolidLayerFacingEverySide() {
            List<BakedQuad> quads = loneCore(15, mock(TextureAtlasSprite.class));
            Set<Direction> faces = quads.stream().map(BakedQuad::direction)
                    .collect(Collectors.toCollection(() -> EnumSet.noneOf(Direction.class)));
            assertEquals(EnumSet.allOf(Direction.class), faces);
            assertTrue(quads.stream().allMatch(quad -> quad.materialInfo().layer() == ChunkSectionLayer.SOLID));
        }
    }

    @Nested
    class Join {

        @Test
        void aJoinedSideDrawsNoQuadOnTheSharedFace() {
            List<BakedQuad> quads = SlimeCoreQuads.quadsOf(new SameGooCells().at(0, 0, 0, 15).at(1, 0, 0, 15),
                    coreMaterial(mock(TextureAtlasSprite.class)));
            assertTrue(quads.stream().noneMatch(inPlaneX(1f)), "a quad lies on the shared east face");
        }

        @Test
        void anUnjoinedSideDrawsItsSideQuadTwoFifteenthsInFromTheFace() {
            List<BakedQuad> quads = loneCore(15, mock(TextureAtlasSprite.class));
            assertTrue(quads.stream()
                    .anyMatch(inPlaneX(13 * FIFTEENTH).and(quad -> quad.direction() == Direction.EAST)));
            assertTrue(quads.stream().noneMatch(inPlaneX(1f)));
        }

        @Test
        void theTopsTextureRunsOnAcrossTheJoin() {
            TextureAtlasSprite fluid = wholeSprite();
            List<BakedQuad> west = SlimeCoreQuads.quadsOf(new SameGooCells().at(0, 0, 0, 15).at(1, 0, 0, 15),
                    coreMaterial(fluid));
            List<BakedQuad> east = SlimeCoreQuads.quadsOf(new SameGooCells().at(0, 0, 0, 15).at(-1, 0, 0, 15),
                    coreMaterial(fluid));
            List<float[]> westEdge = topCornersAtX(west, 1f);
            List<float[]> eastEdge = topCornersAtX(east, 0f);
            assertFalse(westEdge.isEmpty());
            for (float[] corner : westEdge) {
                assertTrue(eastEdge.stream().anyMatch(other -> Math.abs(other[0] - corner[0]) < EPSILON
                                && sameInTile(other[1], corner[1]) && sameInTile(other[2], corner[2])),
                        "the east cell's top meets the west cell's at z " + corner[0] + " in the same texel");
            }
        }
    }

    @Nested
    class Stack {

        @Test
        void theMiddleOfAStackDrawsNoQuadOnItsTopOrFloor() {
            List<BakedQuad> quads = SlimeCoreQuads.quadsOf(new SameGooCells().at(0, 0, 0, 15).at(0, -1, 0, 15)
                    .at(0, 1, 0, 15), coreMaterial(mock(TextureAtlasSprite.class)));
            assertFalse(quads.isEmpty());
            assertTrue(quads.stream().noneMatch(inPlaneY(0f)), "a quad lies on the floor it shares");
            assertTrue(quads.stream().noneMatch(inPlaneY(1f)), "a quad lies on the top it shares");
        }

        @Test
        void aCoreReachingItsTopUnderACellHoldingNoCoreDrawsItsTop() {
            List<BakedQuad> quads = SlimeCoreQuads.quadsOf(new SameGooCells().at(0, 0, 0, 15).at(0, 1, 0, 2),
                    coreMaterial(mock(TextureAtlasSprite.class)));
            assertTrue(quads.stream().anyMatch(inPlaneY(1f).and(quad -> quad.direction() == Direction.UP)));
        }

        /**
         * The shell's top stands at its layers' height and culls only at a
         * whole block under a cell of its goo, so a core top on that plane
         * anywhere else would z-fight it. Seeded neighbourhoods of random
         * cells sweep the rule.
         */
        @Test
        void aCoreTopMeetsItsShellsTopOnlyWhereTheShellCullsItsTop() {
            Random random = new Random(SWEEP_SEED);
            BakedQuad.MaterialInfo material = coreMaterial(mock(TextureAtlasSprite.class));
            for (int trial = 0; trial < SWEEP_TRIALS; trial++) {
                SameGooCells cells = randomNeighbourhood(random);
                int layers = cells.layersAt(0, 0, 0);
                boolean shellTopCulls = layers == WHOLE_LAYERS && cells.layersAt(0, 1, 0) > 0;
                boolean meetsShellTop = SlimeCoreQuads.quadsOf(cells, material).stream()
                        .anyMatch(inPlaneY(layers * FIFTEENTH).and(quad -> quad.direction() == Direction.UP));
                assertFalse(meetsShellTop && !shellTopCulls,
                        "a core top lies on its shell's drawn top at " + layers + " layers, trial " + trial);
            }
        }
    }

    private static SameGooCells randomNeighbourhood(Random random) {
        SameGooCells cells = new SameGooCells();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (random.nextBoolean()) {
                        cells.at(dx, dy, dz, 1 + random.nextInt(WHOLE_LAYERS));
                    }
                }
            }
        }
        return cells.at(0, 0, 0, 1 + random.nextInt(WHOLE_LAYERS));
    }

    private static Predicate<BakedQuad> inPlaneX(float x) {
        return quad -> allCorners(quad, corner -> Math.abs(corner.x() - x) < EPSILON);
    }

    private static Predicate<BakedQuad> inPlaneY(float y) {
        return quad -> allCorners(quad, corner -> Math.abs(corner.y() - y) < EPSILON);
    }

    private static boolean allCorners(BakedQuad quad, Predicate<Vector3fc> test) {
        for (int vertex = 0; vertex < BakedQuad.VERTEX_COUNT; vertex++) {
            if (!test.test(quad.position(vertex))) {
                return false;
            }
        }
        return true;
    }

    /**
     * The top face corners standing at an x: their z, u and v.
     */
    private static List<float[]> topCornersAtX(List<BakedQuad> quads, float x) {
        List<float[]> corners = new ArrayList<>();
        for (BakedQuad quad : quads) {
            if (quad.direction() != Direction.UP) {
                continue;
            }
            for (int vertex = 0; vertex < BakedQuad.VERTEX_COUNT; vertex++) {
                if (Math.abs(quad.position(vertex).x() - x) < EPSILON) {
                    long uv = quad.packedUV(vertex);
                    corners.add(new float[]{quad.position(vertex).z(), UVPair.unpackU(uv), UVPair.unpackV(uv)});
                }
            }
        }
        return corners;
    }

    /**
     * Whether two texture coordinates land on the same spot of a tiling texture.
     */
    private static boolean sameInTile(float a, float b) {
        float apart = Math.abs(a - b);
        return Math.abs(apart - Math.round(apart)) < EPSILON;
    }

    /**
     * Asserts the quads' corners reach exactly the box's bounds.
     */
    private static void assertSpans(List<BakedQuad> quads, SlimeCoreBox box) {
        assertEquals(6, quads.size());
        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float minZ = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        float maxZ = -Float.MAX_VALUE;
        for (BakedQuad quad : quads) {
            for (int vertex = 0; vertex < BakedQuad.VERTEX_COUNT; vertex++) {
                Vector3fc corner = quad.position(vertex);
                minX = Math.min(minX, corner.x());
                minY = Math.min(minY, corner.y());
                minZ = Math.min(minZ, corner.z());
                maxX = Math.max(maxX, corner.x());
                maxY = Math.max(maxY, corner.y());
                maxZ = Math.max(maxZ, corner.z());
            }
        }
        assertEquals(box.minX() * FIFTEENTH, minX, EPSILON);
        assertEquals(box.minY() * FIFTEENTH, minY, EPSILON);
        assertEquals(box.minZ() * FIFTEENTH, minZ, EPSILON);
        assertEquals(box.maxX() * FIFTEENTH, maxX, EPSILON);
        assertEquals(box.maxY() * FIFTEENTH, maxY, EPSILON);
        assertEquals(box.maxZ() * FIFTEENTH, maxZ, EPSILON);
    }
}
