package com.mercuriusxeno.goo.client.model;

import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The glove body submits on the cutout block-atlas type and the palm blob's
 * core on the solid one (decision diagnose-and-correct-the-palm-core-cull).
 */
class GloveSpecialRendererTest {

    private static final Identifier BLOCK_ATLAS = Identifier.withDefaultNamespace("textures/atlas/blocks.png");

    @Test
    void theBodySubmitsOnTheCutoutBlockAtlasType() {
        assertEquals(RenderTypes.entityCutout(BLOCK_ATLAS), GloveSpecialRenderer.bodyType());
    }

    @Test
    void theCoreSubmitsOnTheSolidBlockAtlasType() {
        assertEquals(RenderTypes.entitySolid(BLOCK_ATLAS), GloveSpecialRenderer.coreType());
    }
}
