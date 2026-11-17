package com.mercuriusxeno.goo.client.model;

import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;
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
     * no top, and its rims stand flat at its volume.
     * stacked-goo-cells-share-one-slime-core
     */
    @Test
    void aBlockUnderABlockOfItsGooDrawsNoTop() {
        SlimeTop under = SlimeTop.of(new SameGooCells().at(0, 0, 0, 15).at(0, 1, 0, 6));

        assertTrue(SlimeTopQuads.topOf(under, SHELL).isEmpty());
        assertEquals(15, under.heightAt(3, 1), EPSILON);
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
