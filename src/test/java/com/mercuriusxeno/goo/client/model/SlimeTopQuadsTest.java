package com.mercuriusxeno.goo.client.model;

import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * The quads a goo block's sloped top and its rims draw as, the shell every
 * world goo's block draws its floor's goo through.
 * goo-block-top-slopes-to-the-average-with-the-adjoining-block
 */
class SlimeTopQuadsTest {

    private static final float EPSILON = 1e-5f;
    private static final float ONE_LAYER = 1f / 15;
    private static final BakedQuad.MaterialInfo SHELL = new BakedQuad.MaterialInfo(mock(TextureAtlasSprite.class),
            ChunkSectionLayer.TRANSLUCENT, null, SlimeCoreModel.SHELL_TINT, true, 0, false);

    /**
     * A block of 12 beside a block of 6 to its east: the vertices the 12
     * draws on its east edge and the 6 on its west edge stand at the same
     * heights, 9 layers along the middle of the edge, and neither draws a
     * rim facing along that edge, so no step stands between them.
     */
    @Test
    void twoAdjoiningBlocksMeetAtOneHeightWithNoRimBetweenThem() {
        SameGooCells cells = new SameGooCells().at(0, 0, 0, 12).at(1, 0, 0, 6);
        List<BakedQuad> twelve = quadsOf(SlimeTop.of(cells));
        List<BakedQuad> six = quadsOf(SlimeTop.of(cells.shiftedBy(1, 0, 0)));

        Map<Float, Float> twelvesEdge = heightsAlong(twelve, 1f);
        Map<Float, Float> sixsEdge = heightsAlong(six, 0f);
        assertFalse(twelvesEdge.isEmpty(), "the 12 should draw its east edge");
        assertEquals(twelvesEdge.keySet(), sixsEdge.keySet(), "both should draw the edge at the same points");
        twelvesEdge.forEach((z, y) -> assertEquals(y, sixsEdge.get(z), EPSILON, "the edge at z " + z));
        twelvesEdge.forEach((z, y) -> {
            if (z >= 0.25f - EPSILON && z <= 0.75f + EPSILON) {
                assertEquals(9 * ONE_LAYER, y, EPSILON, "the middle of the edge at z " + z);
            }
        });
        assertTrue(twelve.stream().noneMatch(quad -> quad.direction() == Direction.EAST), "the 12 draws no east rim");
        assertTrue(six.stream().noneMatch(quad -> quad.direction() == Direction.WEST), "the 6 draws no west rim");
    }

    /**
     * A side no goo continues past draws a rim from the floor up to the top's edge.
     */
    @Test
    void aSideToAirDrawsARimUpToTheEdge() {
        List<BakedQuad> rims = SlimeTopQuads.rimsOf(SlimeTop.of(LayersAround.alone(15)), SHELL)
                .getOrDefault(Direction.EAST, List.of());

        assertFalse(rims.isEmpty(), "the east side should draw a rim");
        float tallest = 0;
        for (BakedQuad rim : rims) {
            for (int vertex = 0; vertex < BakedQuad.VERTEX_COUNT; vertex++) {
                tallest = Math.max(tallest, rim.position(vertex).y());
            }
        }
        assertEquals(7.5f * ONE_LAYER, tallest, EPSILON, "the rim reaches the edge's 7.5 layers");
    }

    /**
     * A block a block of its goo stands on is the column's inside: it draws
     * no top, and its rims rise to the column's slope, 10.5 layers at an
     * edge to air under a block of 6, the average of the column's 21 and nothing.
     * stacked-goo-cells-share-one-slime-core
     */
    @Test
    void aBlockUnderABlockOfItsGooDrawsNoTop() {
        SlimeTop under = SlimeTop.of(new SameGooCells().at(0, 0, 0, 15).at(0, 1, 0, 6));

        assertTrue(SlimeTopQuads.topOf(under, SHELL).isEmpty());
        assertEquals(10.5, under.heightAt(3, 1), EPSILON);
    }

    /**
     * A block of 8 attached to its north and south walls with no floor
     * divides its goo between the two slabs: no vertex of either stands more
     * than 4 layers out from its own wall, and their middles stand 7 layers apart.
     * attached-goo-draws-per-attachment-and-divides-its-volume
     */
    @Test
    void opposingSlabsDivideTheBlocksGooBetweenThem() {
        SameGooCells block = new SameGooCells().at(0, 0, 0, 8).share(0, 0, 0, Direction.DOWN, 0)
                .share(0, 0, 0, Direction.NORTH, 4).share(0, 0, 0, Direction.SOUTH, 4);
        List<BakedQuad> north = SlimeTopQuads.topOf(SlimeTop.onSurface(block, Direction.NORTH), SHELL);
        List<BakedQuad> south = SlimeTopQuads.topOf(SlimeTop.onSurface(block, Direction.SOUTH), SHELL);

        assertFalse(north.isEmpty());
        assertFalse(south.isEmpty());
        float northDeepest = 0;
        float southDeepest = 0;
        float northMiddle = 0;
        float southMiddle = 0;
        for (BakedQuad quad : north) {
            for (int vertex = 0; vertex < BakedQuad.VERTEX_COUNT; vertex++) {
                Vector3fc at = quad.position(vertex);
                northDeepest = Math.max(northDeepest, at.z());
                northMiddle = isMiddle(at) ? at.z() : northMiddle;
            }
        }
        for (BakedQuad quad : south) {
            for (int vertex = 0; vertex < BakedQuad.VERTEX_COUNT; vertex++) {
                Vector3fc at = quad.position(vertex);
                southDeepest = Math.max(southDeepest, 1 - at.z());
                southMiddle = isMiddle(at) ? at.z() : southMiddle;
            }
        }
        assertEquals(4 * ONE_LAYER, northDeepest, EPSILON, "the north slab's deepest point");
        assertEquals(4 * ONE_LAYER, southDeepest, EPSILON, "the south slab's deepest point");
        assertEquals(7 * ONE_LAYER, southMiddle - northMiddle, EPSILON, "the slabs' middles apart");
    }

    /**
     * A block of 8 on top of a pillar and a block of 4 attached to the
     * pillar's east face beside it, with no floor: both slabs' edges at the
     * pillar's top edge stand at 6 layers, one sloped face spans from the
     * top slab's edge to the side slab's, and neither draws a rim there.
     * attached-goo-draws-per-attachment-and-divides-its-volume
     */
    @Test
    void gooOnTopAndGooOnTheSideJoinRoundTheEdge() {
        SameGooCells cells = new SameGooCells().at(0, 0, 0, 8).solid(0, -1, 0).at(1, -1, 0, 4)
                .share(1, -1, 0, Direction.DOWN, 0).share(1, -1, 0, Direction.WEST, 4);
        SlimeTop top = SlimeTop.of(cells);
        SlimeTop side = SlimeTop.onSurface(cells.shiftedBy(1, -1, 0), Direction.WEST);

        for (int along : new int[] {1, 2}) {
            assertEquals(6, top.heightAt(3, along), EPSILON, "the top slab's east edge");
            assertEquals(6, side.heightAt(along, 3), EPSILON, "the side slab's top edge");
        }
        assertFalse(top.rims().contains(Direction.EAST), "the top slab draws a rim on the joined edge");
        assertFalse(side.rims().contains(Direction.UP), "the side slab draws a rim on the joined edge");
        List<BakedQuad> joins = new ArrayList<>(SlimeTopQuads.joinsOf(top, SHELL));
        joins.addAll(SlimeTopQuads.joinsOf(side, SHELL));
        assertTrue(joins.stream().anyMatch(quad -> spans(quad, new Vector3f(1, 6 * ONE_LAYER, 0.25f),
                        new Vector3f(1 + 6 * ONE_LAYER, 0, 0.25f))),
                "one face should span from the top slab's edge to the side slab's");
    }

    /**
     * Blocks of 6 and 3 attached to the east and south faces of one pillar
     * join round its upright corner at 4.5 layers; a block of 4 attached to
     * a block's east face and a block of 2 under that block join round its
     * bottom edge at 3; each pair by one sloped face.
     * attached-goo-draws-per-attachment-and-divides-its-volume
     */
    @Test
    void gooOnTwoFacesOfOneBlockJoinsRoundTheEdgeBetweenThem() {
        SameGooCells pillar = new SameGooCells().solid(0, 0, 0).at(1, 0, 0, 6).share(1, 0, 0, Direction.DOWN, 0)
                .share(1, 0, 0, Direction.WEST, 6).at(0, 0, 1, 3).share(0, 0, 1, Direction.DOWN, 0)
                .share(0, 0, 1, Direction.NORTH, 3);
        SlimeTop east = SlimeTop.onSurface(pillar.shiftedBy(1, 0, 0), Direction.WEST);
        SlimeTop south = SlimeTop.onSurface(pillar.shiftedBy(0, 0, 1), Direction.NORTH);
        for (int along : new int[] {1, 2}) {
            assertEquals(4.5, east.heightAt(3, along), EPSILON, "the east slab's south edge");
            assertEquals(4.5, south.heightAt(3, along), EPSILON, "the south slab's east edge");
        }
        assertFalse(SlimeTopQuads.joinsOf(east, SHELL).isEmpty() && SlimeTopQuads.joinsOf(south, SHELL).isEmpty(),
                "the corner should draw a joining face");

        SameGooCells ledge = new SameGooCells().solid(0, 0, 0).at(1, 0, 0, 4).share(1, 0, 0, Direction.DOWN, 0)
                .share(1, 0, 0, Direction.WEST, 4).at(0, -1, 0, 2).share(0, -1, 0, Direction.DOWN, 0)
                .share(0, -1, 0, Direction.UP, 2);
        SlimeTop wall = SlimeTop.onSurface(ledge.shiftedBy(1, 0, 0), Direction.WEST);
        SlimeTop under = SlimeTop.onSurface(ledge.shiftedBy(0, -1, 0), Direction.UP);
        for (int along : new int[] {1, 2}) {
            assertEquals(3, wall.heightAt(along, 0), EPSILON, "the wall slab's foot");
            assertEquals(3, under.heightAt(3, along), EPSILON, "the ceiling slab's east edge");
        }
        assertFalse(SlimeTopQuads.joinsOf(wall, SHELL).isEmpty() && SlimeTopQuads.joinsOf(under, SHELL).isEmpty(),
                "the edge should draw a joining face");
    }

    /** Whether a quad has a vertex at each of two points, in block units. */
    private static boolean spans(BakedQuad quad, Vector3fc one, Vector3fc other) {
        boolean meetsOne = false;
        boolean meetsOther = false;
        for (int vertex = 0; vertex < BakedQuad.VERTEX_COUNT; vertex++) {
            meetsOne |= quad.position(vertex).distance(one) < EPSILON;
            meetsOther |= quad.position(vertex).distance(other) < EPSILON;
        }
        return meetsOne && meetsOther;
    }

    /** Whether a vertex stands at the middle of a wall slab's plan, 8 texels across and up. */
    private static boolean isMiddle(Vector3fc at) {
        return at.x() > 0.25f - EPSILON && at.x() < 0.75f + EPSILON && at.y() > 0.25f - EPSILON
                && at.y() < 0.75f + EPSILON;
    }

    /**
     * Only a full block under goo of its type is the inside of a column: a
     * block of 8 with goo of its type hanging above it still draws its top,
     * so no hole opens in the pile under that goo.
     * stacked-goo-cells-share-one-slime-core
     */
    @Test
    void aBlockShortOfFullDrawsItsTopUnderGooAboveIt() {
        SlimeTop partial = SlimeTop.of(new SameGooCells().at(0, 0, 0, 8).at(0, 1, 0, 3));

        assertFalse(SlimeTopQuads.topOf(partial, SHELL).isEmpty(), "the block of 8 draws no top");
        assertEquals(4, partial.heightAt(3, 1), EPSILON, "its edge to air slopes to half its volume");
    }

    private static List<BakedQuad> quadsOf(SlimeTop top) {
        List<BakedQuad> quads = new ArrayList<>(SlimeTopQuads.topOf(top, SHELL));
        SlimeTopQuads.rimsOf(top, SHELL).values().forEach(quads::addAll);
        return quads;
    }

    /** The height of every vertex a block's quads draw at one x, keyed by its z. */
    private static Map<Float, Float> heightsAlong(List<BakedQuad> quads, float x) {
        Map<Float, Float> heights = new TreeMap<>();
        for (BakedQuad quad : quads) {
            for (int vertex = 0; vertex < BakedQuad.VERTEX_COUNT; vertex++) {
                Vector3fc at = quad.position(vertex);
                if (Math.abs(at.x() - x) < EPSILON && quad.direction() == Direction.UP) {
                    heights.merge(Math.round(at.z() * 16) / 16f, at.y(), Math::max);
                }
            }
        }
        return heights;
    }
}
