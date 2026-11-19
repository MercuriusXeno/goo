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
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Covers the quads a slime core draws as: a translucent body at half
 * opacity on the cell's goo type's fluid sprite, in the sprite's own colors,
 * the shell's sloped body at the slime's 3/4, centred: from 1/8 to 7/8 of
 * the shell's height at every point, 1/8 of a block in from each side no goo
 * of its type continues past, and drawing nothing on a face its block
 * shares with goo of its type, textured where it stands in its block so the
 * texture runs on across a join. The sprites stand in for the goo types'.
 */
class SlimeCoreQuadsTest {

    private static final float FIFTEENTH = 1f / 15f;
    private static final float PIXEL = 1f / 16f;
    private static final float EPSILON = 1e-5f;
    private static final int WHOLE_LAYERS = 15;
    private static final long SWEEP_SEED = 7L;
    private static final int SWEEP_TRIALS = 2000;
    /** The core's top, as a share of the shell's height over the same point: the slime's 3/4, centred. */
    private static final float CORE_TOP = 7f / 8;
    /** The core's floor, as a share of the shell's height over the same point. */
    private static final float CORE_FLOOR = 1f / 8;
    /** The core's margin in from each open side, in blocks. */
    private static final float SIDE_MARGIN = 2 * PIXEL;
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

    private static List<BakedQuad> coreOf(LayersAround around) {
        return SlimeCoreQuads.quadsOf(around, coreMaterial(mock(TextureAtlasSprite.class)));
    }

    @Nested
    class Contour {

        /**
         * Every point of the core stands between 1/8 and 7/8 of the shell's
         * height above it and 2 px or more in from each open side, for a block
         * of 12 beside a block of 3, a block of 15 alone and a block of 1 alone.
         * slime-core-slopes-with-the-pile-inside-its-contour
         * residual-always-draws-a-core-inside-a-one-eighth-margin
         */
        @Test
        void theCoreStaysInsideTheSlopedTop() {
            SameGooCells pair = new SameGooCells().at(0, 0, 0, 12).at(1, 0, 0, 3);
            assertInsideItsContour(pair, Set.of(Direction.NORTH, Direction.SOUTH, Direction.WEST));
            assertInsideItsContour(pair.shiftedBy(1, 0, 0), Set.of(Direction.NORTH, Direction.SOUTH, Direction.EAST));
            Set<Direction> open = EnumSet.of(Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST);
            assertInsideItsContour(LayersAround.alone(15), open);
            assertInsideItsContour(LayersAround.alone(1), open);
        }

        /**
         * At the inner corner of a film, a block whose north and east
         * neighbours hold goo and whose north-east diagonal holds none keeps
         * its core 2 px in from that corner, as its neighbours' cores stand 2
         * px in from the empty block, and walls the notch it leaves: an east
         * side at 14 px along the corner's north 2 px, a north side at 2 px
         * along its east 2 px, so the core's outline runs on round the corner.
         * slime-core-slopes-with-the-pile-inside-its-contour
         * residual-always-draws-a-core-inside-a-one-eighth-margin
         */
        @Test
        void anInnerCornerKeepsTheCoreInsetAndWalled() {
            SameGooCells film = new SameGooCells().at(0, 0, 0, 1).at(0, 0, -1, 1).at(1, 0, 0, 1);
            List<BakedQuad> core = coreOf(film);
            float inner = 1 - SIDE_MARGIN;

            assertTrue(core.stream().noneMatch(quad -> quad.direction().getAxis().isVertical() && allCorners(quad,
                            at -> at.x() > inner - EPSILON && at.z() < SIDE_MARGIN + EPSILON)),
                    "no core top or floor should stand in the corner's 2 px square");
            assertTrue(core.stream().anyMatch(quad -> quad.direction() == Direction.EAST
                            && allCorners(quad, at -> Math.abs(at.x() - inner) < EPSILON && at.z() < SIDE_MARGIN + EPSILON)),
                    "the core should wall the notch with an east side at 14 px");
            assertTrue(core.stream().anyMatch(quad -> quad.direction() == Direction.NORTH
                            && allCorners(quad, at -> Math.abs(at.z() - SIDE_MARGIN) < EPSILON && at.x() > inner - EPSILON)),
                    "the core should wall the notch with a north side at 2 px");
        }

        /**
         * A block of 1 alone keeps a core under its middle, where the slope leaves it room.
         * residual-always-draws-a-core-inside-a-one-eighth-margin
         */
        @Test
        void aBlockOfOneLayerAloneDrawsACoreUnderItsMiddle() {
            assertTrue(coreOf(LayersAround.alone(1)).stream().anyMatch(quad -> quad.direction() == Direction.UP
                    && Math.abs(middleOf(quad).x() - 0.5f) < EPSILON && Math.abs(middleOf(quad).z() - 0.5f) < EPSILON));
        }

        /**
         * Seeded neighbourhoods of random cells sweep the contour rule: no
         * core vertex stands over 7/8 of its shell's height, save
         * in a block goo of its type covers, which draws no top and whose
         * core runs on up the column.
         * slime-core-slopes-with-the-pile-inside-its-contour
         * stacked-goo-cells-share-one-slime-core
         */
        @Test
        void noCoreVertexRisesIntoItsShellsMargin() {
            Random random = new Random(SWEEP_SEED);
            for (int trial = 0; trial < SWEEP_TRIALS; trial++) {
                SameGooCells cells = randomNeighbourhood(random);
                SlimeTop top = SlimeTop.of(cells);
                if (top.covered()) {
                    continue;
                }
                for (BakedQuad quad : coreOf(cells)) {
                    for (int vertex = 0; vertex < BakedQuad.VERTEX_COUNT; vertex++) {
                        Vector3fc at = quad.position(vertex);
                        assertTrue(at.y() <= shellAt(top, at) * CORE_TOP + EPSILON,
                                "a core vertex at " + at + " rises over 7/8 of its shell at trial " + trial);
                    }
                }
            }
        }

        private void assertInsideItsContour(LayersAround around, Set<Direction> open) {
            SlimeTop top = SlimeTop.of(around);
            List<BakedQuad> quads = coreOf(around);
            assertFalse(quads.isEmpty(), "the block should draw a core");
            for (BakedQuad quad : quads) {
                for (int vertex = 0; vertex < BakedQuad.VERTEX_COUNT; vertex++) {
                    Vector3fc at = quad.position(vertex);
                    assertTrue(at.y() <= shellAt(top, at) * CORE_TOP + EPSILON,
                            "a core vertex at " + at + " stands over 7/8 of the shell's height");
                    assertTrue(at.y() >= shellAt(top, at) * CORE_FLOOR - EPSILON,
                            "a core vertex at " + at + " stands under 1/8 of the shell's height");
                    for (Direction side : open) {
                        assertTrue(inFrom(side, at) >= SIDE_MARGIN - EPSILON,
                                "a core vertex at " + at + " stands within 2 px of the open " + side + " side");
                    }
                }
            }
        }
    }

    @Nested
    class Join {

        /**
         * Cores of a block of 12 and a block of 6 beside it meet at one
         * height on the edge they share, and neither draws a face there.
         * slime-core-slopes-with-the-pile-inside-its-contour
         */
        @Test
        void neighbouringCoresMeetAtOneHeightOnTheirSharedEdge() {
            SameGooCells pair = new SameGooCells().at(0, 0, 0, 12).at(1, 0, 0, 6);
            List<BakedQuad> twelve = coreOf(pair);
            List<BakedQuad> six = coreOf(pair.shiftedBy(1, 0, 0));

            Map<Float, Float> twelvesEdge = topHeightsAlong(twelve, 1f);
            Map<Float, Float> sixsEdge = topHeightsAlong(six, 0f);
            assertFalse(twelvesEdge.isEmpty(), "the 12's core should reach its east edge");
            assertEquals(twelvesEdge.keySet(), sixsEdge.keySet());
            twelvesEdge.forEach((z, y) -> assertEquals(y, sixsEdge.get(z), EPSILON, "the cores' edge at z " + z));
            assertTopFollowsTheSlope(twelve, SlimeTop.of(pair));
            assertTopFollowsTheSlope(six, SlimeTop.of(pair.shiftedBy(1, 0, 0)));
            assertTrue(twelve.stream().noneMatch(inPlaneX(1f)), "the 12 draws a core face on the shared edge");
            assertTrue(six.stream().noneMatch(inPlaneX(0f)), "the 6 draws a core face on the shared edge");
        }

        /** Every core top vertex stands at exactly 7/8 of the sloped top at its place, so no step stands. */
        private void assertTopFollowsTheSlope(List<BakedQuad> core, SlimeTop top) {
            for (BakedQuad quad : core) {
                for (int vertex = 0; quad.direction() == Direction.UP && vertex < BakedQuad.VERTEX_COUNT; vertex++) {
                    Vector3fc at = quad.position(vertex);
                    assertEquals(shellAt(top, at) * CORE_TOP, at.y(), EPSILON, "the core's top at " + at);
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
            List<BakedQuad> quads = coreOf(field);
            assertTrue(quads.stream().allMatch(quad -> quad.direction().getAxis().isVertical()),
                    "the middle of a joined field draws a side face at "
                            + quads.stream().filter(quad -> quad.direction().getAxis().isHorizontal())
                            .map(quad -> middleOf(quad).toString()).toList());
        }

        @Test
        void anUnjoinedSideDrawsItsSideTwoPixelsInFromTheFace() {
            List<BakedQuad> quads = coreOf(LayersAround.alone(15));
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
            List<BakedQuad> quads = coreOf(new SameGooCells().at(0, 0, 0, 15).at(0, -1, 0, 15).at(0, 1, 0, 15));
            assertFalse(quads.isEmpty());
            assertTrue(quads.stream().noneMatch(inPlaneY(0f)), "a quad lies on the floor it shares");
            assertTrue(quads.stream().noneMatch(inPlaneY(1f)), "a quad lies on the top it shares");
        }

        /**
         * A full block with nothing on it beside a whole cube, a full block
         * under a block of 9, meets the cube's core along their edge: the
         * cube's core steps down along that edge to the height of the
         * uncovered block's core at every point of it, so no core top hangs
         * over the other with nothing closing the step.
         * full-goo-block-under-goo-is-a-whole-cube
         */
        @Test
        void anUncoveredFullBlocksCoreMeetsTheCubesCoreBesideIt() {
            SameGooCells pit = new SameGooCells().at(0, 0, 0, 15).at(0, 1, 0, 9).at(1, 0, 0, 15);
            Map<Float, Float> cube = topHeightsAlong(coreOf(pit), 1f);
            Map<Float, Float> uncovered = topHeightsAlong(coreOf(pit.shiftedBy(1, 0, 0)), 0f);

            assertFalse(cube.isEmpty(), "the cube should draw its core's step along the edge");
            assertEquals(cube.keySet(), uncovered.keySet(), "both cores should draw the edge at the same points");
            cube.forEach((z, y) -> assertEquals(y, uncovered.get(z), EPSILON, "the cores' tops at z " + z));
        }

        /**
         * The middle block of a pillar of 15, 15 and 6 alone on a solid
         * floor, a whole cube, keeps its core 2 px in from each side it
         * shows and runs it on through its floor and its top into the cores
         * of the goo under and over it, drawing no face on either.
         * full-goo-block-under-goo-is-a-whole-cube
         * residual-always-draws-a-core-inside-a-one-eighth-margin
         * stacked-goo-cells-share-one-slime-core
         */
        @Test
        void aCoveredCubesCoreKeepsItsSideMarginAndRunsThrough() {
            List<BakedQuad> core = coreOf(SameGooCells.pillar(false).shiftedBy(0, 1, 0));
            Set<Direction> sides = EnumSet.noneOf(Direction.class);
            for (BakedQuad quad : core) {
                Direction face = quad.direction();
                assertTrue(face.getAxis().isHorizontal(), "the cube's core draws a " + face + " face");
                float inward = face.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1 - SIDE_MARGIN
                        : SIDE_MARGIN;
                assertTrue(face.getAxis() == Direction.Axis.X ? inPlaneX(inward).test(quad)
                        : allCorners(quad, at -> Math.abs(at.z() - inward) < EPSILON), "the " + face + " side at "
                        + middleOf(quad));
                sides.add(face);
            }
            assertEquals(EnumSet.of(Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST), sides);
        }

        /**
         * A block of 6 on a full block of its goo keeps one unbroken core with
         * it: neither draws a core face on the face the two share.
         * stacked-goo-cells-share-one-slime-core
         */
        @Test
        void aBlockOnAFullBlockKeepsOneCoreWithIt() {
            SameGooCells column = new SameGooCells().at(0, 0, 0, 6).at(0, -1, 0, 15);
            assertTrue(coreOf(column).stream().noneMatch(inPlaneY(0f)), "the upper block draws a core floor");
            assertTrue(coreOf(column.shiftedBy(0, -1, 0)).stream().noneMatch(inPlaneY(1f)),
                    "the full block draws a core top");
        }

    }

    @Nested
    class Inside {

        /**
         * A pile of Goop, a cone of blocks on stone with column tops over its
         * full middle: no block's core draws a side anywhere but its own outer
         * bound, 2 px in from a side no goo continues past or round the notch
         * at a corner the pile's edge turns in round, so no wall stands
         * inside the pile.
         * slime-core-slopes-with-the-pile-inside-its-contour
         */
        @Test
        void noCoreSideStandsInsideThePile() {
            int[][] lower = {
                    {0, 2, 4, 5, 4, 2, 0},
                    {2, 6, 10, 12, 10, 6, 2},
                    {4, 10, 15, 15, 15, 10, 4},
                    {5, 12, 15, 15, 15, 12, 5},
                    {4, 10, 15, 15, 14, 10, 4},
                    {2, 6, 10, 12, 10, 6, 2},
                    {0, 2, 4, 5, 4, 2, 0}};
            int[][] upper = {
                    {0, 0, 0, 0, 0, 0, 0},
                    {0, 0, 0, 0, 0, 0, 0},
                    {0, 0, 2, 4, 1, 0, 0},
                    {0, 0, 5, 9, 3, 0, 0},
                    {0, 0, 1, 3, 0, 0, 0},
                    {0, 0, 0, 0, 0, 0, 0},
                    {0, 0, 0, 0, 0, 0, 0}};
            SameGooCells pile = new SameGooCells();
            for (int x = 0; x < lower.length; x++) {
                for (int z = 0; z < lower.length; z++) {
                    pile.solid(x - 3, -1, z - 3);
                    if (lower[x][z] > 0) {
                        pile.at(x - 3, 0, z - 3, lower[x][z]);
                    }
                    if (upper[x][z] > 0) {
                        pile.at(x - 3, 1, z - 3, upper[x][z]);
                    }
                }
            }
            List<String> inside = new ArrayList<>();
            for (int x = -3; x <= 3; x++) {
                for (int y = 0; y <= 1; y++) {
                    for (int z = -3; z <= 3; z++) {
                        if (pile.layersAt(x, y, z) > 0) {
                            inside.addAll(sidesInside(pile.shiftedBy(x, y, z), x + "," + y + "," + z));
                        }
                    }
                }
            }
            assertTrue(inside.isEmpty(), inside.size() + " core sides stand inside the pile: " + inside);
        }

        private List<String> sidesInside(LayersAround around, String block) {
            SlimeTop top = SlimeTop.of(around);
            List<String> inside = new ArrayList<>();
            for (BakedQuad quad : coreOf(around)) {
                Direction face = quad.direction();
                if (face.getAxis().isVertical()) {
                    continue;
                }
                float plane = face.getAxis() == Direction.Axis.X ? middleOf(quad).x() : middleOf(quad).z();
                float bound = face.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1 - SIDE_MARGIN
                        : SIDE_MARGIN;
                boolean bounded = top.rims().contains(face) || top.steps().contains(face) || notchesAt(top, face);
                if (!bounded || Math.abs(plane - bound) > EPSILON) {
                    inside.add(block + " " + face + " at " + plane);
                }
            }
            return inside;
        }

        /** Whether the floor slab's goo turns in round a corner on a side, where the core walls a notch. */
        private boolean notchesAt(SlimeTop top, Direction face) {
            if (face.getAxis() == Direction.Axis.X) {
                return top.turnsInAt(face, Direction.NORTH) || top.turnsInAt(face, Direction.SOUTH);
            }
            return top.turnsInAt(Direction.EAST, face) || top.turnsInAt(Direction.WEST, face);
        }
    }

    @Nested
    class Slabs {

        /**
         * Goo on a wall holds a core as goo on a floor does: a block of 6
         * attached to its north wall alone draws a core standing between 1/8
         * and 7/8 of its slab's thickness out from the wall.
         * operator UAT 2026-11-17: no slime core
         */
        @Test
        void aWallSlabHoldsACoreInsideIt() {
            SameGooCells wall = new SameGooCells().at(0, 0, 0, 6).share(0, 0, 0, Direction.DOWN, 0)
                    .share(0, 0, 0, Direction.NORTH, 6);
            SlimeTop slab = SlimeTop.onSurface(wall, Direction.NORTH);
            List<BakedQuad> quads = SlimeCoreQuads.quadsOf(slab, coreMaterial(mock(TextureAtlasSprite.class)));

            assertFalse(quads.isEmpty(), "the wall slab should hold a core");
            for (BakedQuad quad : quads) {
                for (int vertex = 0; vertex < BakedQuad.VERTEX_COUNT; vertex++) {
                    Vector3fc at = quad.position(vertex);
                    float thickness = (float) slab.heightAtPixel(at.x() * 16, at.y() * 16) * FIFTEENTH;
                    assertTrue(at.z() <= thickness * CORE_TOP + EPSILON && at.z() >= thickness * CORE_FLOOR - EPSILON,
                            "a wall core vertex at " + at + " stands outside 1/8 to 7/8 of its slab");
                }
            }
        }
    }

    @Nested
    class Look {

        /** slime-core-draws-translucent-at-the-shells-opacity */
        @Test
        void theCoreDrawsOnTheTranslucentLayerAtHalfOpacityFacingEverySide() {
            List<BakedQuad> quads = coreOf(LayersAround.alone(15));
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

    /** The shell's top over a point, in blocks above the block's floor. */
    private static float shellAt(SlimeTop top, Vector3fc at) {
        return (float) top.heightAtPixel(at.x() * 16, at.z() * 16) * FIFTEENTH;
    }

    /** How far in from a side a point stands, in blocks. */
    private static float inFrom(Direction side, Vector3fc at) {
        return switch (side) {
            case WEST -> at.x();
            case EAST -> 1 - at.x();
            case NORTH -> at.z();
            case SOUTH -> 1 - at.z();
            default -> Float.MAX_VALUE;
        };
    }

    /** The height of every core top vertex at one x, keyed by its z. */
    private static Map<Float, Float> topHeightsAlong(List<BakedQuad> quads, float x) {
        Map<Float, Float> heights = new TreeMap<>();
        for (BakedQuad quad : quads) {
            for (int vertex = 0; vertex < BakedQuad.VERTEX_COUNT; vertex++) {
                Vector3fc at = quad.position(vertex);
                if (quad.direction() == Direction.UP && Math.abs(at.x() - x) < EPSILON) {
                    heights.merge(Math.round(at.z() * 16) / 16f, at.y(), Math::max);
                }
            }
        }
        return heights;
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
}
