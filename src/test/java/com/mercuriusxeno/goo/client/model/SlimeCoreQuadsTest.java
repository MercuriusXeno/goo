package com.mercuriusxeno.goo.client.model;

import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;
import org.joml.Vector3fc;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Covers the quads a slime core's box draws as: an opaque box on the cell's
 * goo type's fluid sprite, untinted as the palm blob's core is, spanning the
 * box exactly. The sprites stand in for weird's and kinetic's fluid sprites.
 */
class SlimeCoreQuadsTest {

    private static final float FIFTEENTH = 1f / 15f;
    private static final float EPSILON = 1e-5f;

    private static BakedQuad.MaterialInfo coreMaterial(TextureAtlasSprite fluid) {
        return new BakedQuad.MaterialInfo(fluid, ChunkSectionLayer.SOLID, null, SlimeCoreQuads.NO_TINT, true, 0, false);
    }

    @Nested
    class Box {

        @Test
        void slimeAtEightLayersDrawsWeirdsFluidOverTheRulesBox() {
            TextureAtlasSprite weird = mock(TextureAtlasSprite.class);
            List<BakedQuad> quads = SlimeCoreQuads.quadsOf(SlimeCoreBox.of(8), coreMaterial(weird));
            assertSpans(quads, new SlimeCoreBox(2, 2, 2, 13, 6, 13));
            assertTrue(quads.stream().allMatch(quad -> quad.materialInfo().sprite() == weird));
            assertTrue(quads.stream().noneMatch(quad -> quad.materialInfo().isTinted()));
        }

        @Test
        void slickAtEightLayersDrawsKineticsFluidOverTheRulesBox() {
            TextureAtlasSprite kinetic = mock(TextureAtlasSprite.class);
            List<BakedQuad> quads = SlimeCoreQuads.quadsOf(SlimeCoreBox.of(8), coreMaterial(kinetic));
            assertSpans(quads, new SlimeCoreBox(2, 2, 2, 13, 6, 13));
            assertTrue(quads.stream().allMatch(quad -> quad.materialInfo().sprite() == kinetic));
        }

        @Test
        void aCellAtFourLayersDrawsNoCore() {
            assertEquals(List.of(),
                    SlimeCoreQuads.quadsOf(SlimeCoreBox.of(4), coreMaterial(mock(TextureAtlasSprite.class))));
        }

        @Test
        void theCoreDrawsOnTheSolidLayerFacingEverySide() {
            List<BakedQuad> quads = SlimeCoreQuads.quadsOf(SlimeCoreBox.of(15),
                    coreMaterial(mock(TextureAtlasSprite.class)));
            Set<Direction> faces = quads.stream().map(BakedQuad::direction)
                    .collect(Collectors.toCollection(() -> EnumSet.noneOf(Direction.class)));
            assertEquals(EnumSet.allOf(Direction.class), faces);
            assertTrue(quads.stream().allMatch(quad -> quad.materialInfo().layer() == ChunkSectionLayer.SOLID));
        }
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
