package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.client.RecordingVertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import net.minecraft.core.Direction;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The glow fade writes on its consumer only what the additive no-depth
 * pipeline's vertex format carries: a position-color format holds no
 * lightmap, so the glow writes no light (decision diagnose-then-fix-render-math).
 */
class GlowFadeLightTest {

    private static final int QUAD_VERTICES = 4;

    @ParameterizedTest
    @EnumSource(Direction.class)
    void emitAuroraQuadPerVertex(Direction face) {
        VertexFormat format = GooRenderTypes.QUADS_ADDITIVE_NO_DEPTH_PIPELINE.getVertexFormat();
        RecordingVertexConsumer consumer = new RecordingVertexConsumer();

        GlowFadeVisual.emitAuroraQuadPerVertex(new PoseStack().last(), consumer,
                0.5f, 0.5f, 0.5f, 0.5f, 0f, 0f, face, 0f, 1f, 0.2f, 0.3f,
                0xFF40FF40, 0x0040FF40);

        assertEquals(QUAD_VERTICES, consumer.vertices().size());
        int expectedLightWrites = format.contains(VertexFormatElement.UV2) ? QUAD_VERTICES : 0;
        assertEquals(expectedLightWrites, consumer.lightWrites());
    }
}
