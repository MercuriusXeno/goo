package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.program.DegradeStep;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * An exposure's mingled block draws in its own colors untinted, and in the
 * tint it carries, opaque, where an ability sent one
 * (decision decay-gnats-degrade-each-block-once).
 */
class BlockMingleRendererTest {

    @Test
    void anUntintedExposureDrawsInItsOwnColors() {
        assertEquals(0xFFFFFFFF, BlockMingleRenderer.mingleColor(-1));
    }

    // coat-is-a-green-slime-film-over-the-blocks-own-texture
    @Test
    void aTranslucentBlocksQuadDrawsBlended() {
        assertTrue(BlockMingleRenderer.drawsTranslucent(quadOn(ChunkSectionLayer.TRANSLUCENT)));
    }

    @Test
    void aSolidBlocksQuadDrawsOpaque() {
        assertFalse(BlockMingleRenderer.drawsTranslucent(quadOn(ChunkSectionLayer.SOLID)));
    }

    private static BakedQuad quadOn(ChunkSectionLayer layer) {
        return new BakedQuad(new Vector3f(), new Vector3f(), new Vector3f(), new Vector3f(), 0L, 0L, 0L, 0L,
                Direction.UP, new BakedQuad.MaterialInfo(mock(TextureAtlasSprite.class), layer, null, -1, true, 0, true));
    }

    @Test
    void aTintedExposureDrawsInItsTintOpaque() {
        assertEquals(0xFF000000 | DegradeStep.NETHER_MAROON,
                BlockMingleRenderer.mingleColor(DegradeStep.NETHER_MAROON));
    }
}
