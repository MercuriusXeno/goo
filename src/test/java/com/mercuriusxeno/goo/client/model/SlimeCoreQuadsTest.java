package com.mercuriusxeno.goo.client.model;

import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3f;
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
 * Covers the quads a slime core's boxes draw as: a translucent body at the
 * shell's alpha on the cell's goo type's fluid sprite, in the sprite's own
 * colors as the palm blob's core is,
 * spanning its boxes in 120ths of a block, drawing nothing on a face its block shares with a
 * joined core and textured where it stands in its block so the texture runs
 * on across a join. The sprites stand in for weird's and kinetic's.
 */
class SlimeCoreQuadsTest {

    private static final float FIFTEENTH = 1f / 15f;
    private static final float UNIT = 1f / 120f;
    private static final float PIXEL = 1f / 16f;
    private static final float EPSILON = 1e-5f;
    private static final int WHOLE_LAYERS = 15;
    private static final long SWEEP_SEED = 7L;
    private static final int SWEEP_TRIALS = 2000;
    /** A core box's units to a block. */
    private static final float BLOCK_UNITS = 120f;
    /** How far off a quad's middle the side test reads, in blocks: well under a box unit. */
    private static final float HAIR = 1e-3f;
    private static final Identifier BLOCK_ATLAS = Identifier.withDefaultNamespace("textures/atlas/blocks.png");
    /** A goo type's highlight, the shell's tint, which the core never takes. */
    private static final int HIGHLIGHT = 0x3A7F22;
    /** The core's alpha, 50% opaque. */
    private static final int CORE_ALPHA = 0x80;
    /** The layers' alpha, 30% opaque. */
    private static final int SHELL_ALPHA = 0x4C;

    /**
     * The material SlimeCoreModel bakes a core with, over a fluid sprite on the block atlas.
     */
    private static BakedQuad.MaterialInfo coreMaterial(TextureAtlasSprite fluid) {
        when(fluid.atlasLocation()).thenReturn(BLOCK_ATLAS);
        return SlimeCoreModel.coreMaterial(new Material.Baked(fluid, false));
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
            assertSpans(quads, new SlimeCoreBox(15, 3, 15, 105, 61, 105));
            assertTrue(quads.stream().allMatch(quad -> quad.materialInfo().sprite() == weird));
        }

        @Test
        void slickAtEightLayersDrawsKineticsFluidOverTheRulesBox() {
            TextureAtlasSprite kinetic = mock(TextureAtlasSprite.class);
            List<BakedQuad> quads = loneCore(8, kinetic);
            assertSpans(quads, new SlimeCoreBox(15, 3, 15, 105, 61, 105));
            assertTrue(quads.stream().allMatch(quad -> quad.materialInfo().sprite() == kinetic));
        }

        /** residual-always-draws-a-core-inside-a-one-eighth-margin */
        @Test
        void aCellAtOneLayerDrawsItsCoreTwoPixelsInAndFourTenthsOfAPixelUpAndDown() {
            float layerPixels = 16f / WHOLE_LAYERS;
            assertSpansPixels(loneCore(1, mock(TextureAtlasSprite.class)),
                    new float[] {2f, 0.4f, 2f}, new float[] {14f, layerPixels - 0.4f, 14f});
        }

        /** slime-core-draws-translucent-at-the-shells-opacity */
        @Test
        void theCoreDrawsOnTheTranslucentLayerAtHalfOpacityFacingEverySide() {
            List<BakedQuad> quads = loneCore(15, mock(TextureAtlasSprite.class));
            Set<Direction> faces = quads.stream().map(BakedQuad::direction)
                    .collect(Collectors.toCollection(() -> EnumSet.noneOf(Direction.class)));
            List<BlockTintSource> tints = VolumeCellTint.sources(GooTypes.ZOO, type -> HIGHLIGHT);

            assertEquals(EnumSet.allOf(Direction.class), faces);
            for (BakedQuad quad : quads) {
                assertEquals(ChunkSectionLayer.TRANSLUCENT, quad.materialInfo().layer());
                assertEquals(ARGB.color(CORE_ALPHA, 0xFFFFFF),
                        tints.get(quad.materialInfo().tintIndex()).color(mock(BlockState.class)),
                        "the core draws at half opacity in its sprite's own colors");
            }
            assertEquals(ARGB.color(SHELL_ALPHA, HIGHLIGHT), tints.getFirst().color(mock(BlockState.class)),
                    "the layers draw at 30% opacity in the goo's highlight");
        }
    }

    @Nested
    class Join {

        /**
         * A translucent core shows any face drawn inside it, so a face where
         * two of a cell's own boxes meet reads as a wall within the core.
         * Seeded neighbourhoods of random cells sweep the rule.
         * slime-cores-connect-where-only-padding-parts-them
         * slime-core-draws-translucent-at-the-shells-opacity
         */
        @Test
        void aCoreDrawsNoFaceWithCoreOnBothSides() {
            Random random = new Random(SWEEP_SEED);
            BakedQuad.MaterialInfo material = coreMaterial(mock(TextureAtlasSprite.class));
            for (int trial = 0; trial < SWEEP_TRIALS; trial++) {
                SameGooCells cells = randomNeighbourhood(random);
                List<SlimeCoreBox> boxes = SlimeCoreBox.boxesOf(cells);
                for (BakedQuad quad : SlimeCoreQuads.quadsOf(cells, material)) {
                    assertFalse(coreOnBothSides(quad, boxes), "a " + quad.direction() + " face lies inside the core "
                            + "at trial " + trial + ", its middle at " + middleOf(quad));
                }
            }
        }

        @Test
        void theMiddleOfAJoinedFieldDrawsOnlyItsTopAndFloor() {
            SameGooCells field = new SameGooCells();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    field.at(dx, 0, dz, 1);
                }
            }
            List<BakedQuad> quads = SlimeCoreQuads.quadsOf(field, coreMaterial(mock(TextureAtlasSprite.class)));
            assertTrue(quads.stream().allMatch(quad -> quad.direction().getAxis().isVertical()),
                    "the middle of a joined field draws a side face at "
                            + quads.stream().filter(quad -> quad.direction().getAxis().isHorizontal())
                            .map(quad -> middleOf(quad).toString()).toList());
        }

        @Test
        void aJoinedSideDrawsNoQuadOnTheSharedFace() {
            List<BakedQuad> quads = SlimeCoreQuads.quadsOf(new SameGooCells().at(0, 0, 0, 15).at(1, 0, 0, 15),
                    coreMaterial(mock(TextureAtlasSprite.class)));
            assertTrue(quads.stream().noneMatch(inPlaneX(1f)), "a quad lies on the shared east face");
        }

        @Test
        void anUnjoinedSideDrawsItsSideQuadTwoPixelsInFromTheFace() {
            List<BakedQuad> quads = loneCore(15, mock(TextureAtlasSprite.class));
            assertTrue(quads.stream()
                    .anyMatch(inPlaneX(14 * PIXEL).and(quad -> quad.direction() == Direction.EAST)));
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

        /** stacked-goo-cells-share-one-slime-core */
        @Test
        void aColumnsTopCellOfOneLayerDrawsItsCoreTopFourTenthsOfAPixelUnderItsSurface() {
            List<BakedQuad> quads = SlimeCoreQuads.quadsOf(new SameGooCells().at(0, 0, 0, 1).at(0, -1, 0, 15),
                    coreMaterial(mock(TextureAtlasSprite.class)));
            assertTrue(quads.stream().anyMatch(inPlaneY(FIFTEENTH - 0.4f * PIXEL)
                    .and(quad -> quad.direction() == Direction.UP)));
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

    /**
     * Whether core stands on both sides of a quad: a point a hair off its
     * middle on each side lies inside one of the cell's own core boxes, or
     * past the block where the core across covers that side.
     */
    private static boolean coreOnBothSides(BakedQuad quad, List<SlimeCoreBox> boxes) {
        Vector3f middle = middleOf(quad);
        Vector3f step = new Vector3f(quad.direction().getStepX(), quad.direction().getStepY(),
                quad.direction().getStepZ()).mul(HAIR);
        return insideAnyBox(new Vector3f(middle).add(step), boxes)
                && insideAnyBox(new Vector3f(middle).sub(step), boxes);
    }

    private static boolean insideAnyBox(Vector3f point, List<SlimeCoreBox> boxes) {
        Vector3f units = new Vector3f(point).mul(BLOCK_UNITS);
        return boxes.stream().anyMatch(box -> units.x() > box.minX() && units.x() < box.maxX()
                && units.y() > box.minY() && units.y() < box.maxY()
                && units.z() > box.minZ() && units.z() < box.maxZ());
    }

    private static Vector3f middleOf(BakedQuad quad) {
        Vector3f middle = new Vector3f();
        for (int vertex = 0; vertex < BakedQuad.VERTEX_COUNT; vertex++) {
            middle.add(quad.position(vertex));
        }
        return middle.div(BakedQuad.VERTEX_COUNT);
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
        assertSpansPixels(quads, new float[] {box.minX() * UNIT / PIXEL, box.minY() * UNIT / PIXEL,
                box.minZ() * UNIT / PIXEL}, new float[] {box.maxX() * UNIT / PIXEL, box.maxY() * UNIT / PIXEL,
                box.maxZ() * UNIT / PIXEL});
    }

    /**
     * Asserts the quads are one box's faces, one for each side and its top
     * cut into the pieces the shell's top patches stand over, its corners
     * reaching exactly the bounds given in model pixels, x, y and z.
     */
    private static void assertSpansPixels(List<BakedQuad> quads, float[] from, float[] to) {
        for (Direction face : Direction.values()) {
            long drawn = quads.stream().filter(quad -> quad.direction() == face).count();
            assertTrue(face == Direction.UP ? drawn >= 1 : drawn == 1, drawn + " " + face + " faces");
        }
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
        assertEquals(from[0] * PIXEL, minX, EPSILON);
        assertEquals(from[1] * PIXEL, minY, EPSILON);
        assertEquals(from[2] * PIXEL, minZ, EPSILON);
        assertEquals(to[0] * PIXEL, maxX, EPSILON);
        assertEquals(to[1] * PIXEL, maxY, EPSILON);
        assertEquals(to[2] * PIXEL, maxZ, EPSILON);
    }
}
