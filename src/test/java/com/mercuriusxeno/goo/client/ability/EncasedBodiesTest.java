package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.client.RecordingVertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.monster.witch.WitchModel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The encasement's witch body lays its ice over the hat where the hat is
 * drawn: across the brim's ten-texel square, with no box past it.
 */
class EncasedBodiesTest {

    private static final float EPSILON = 1e-4f;
    /** WitchModel's brim: a ten-texel square from five texels either side of the head's middle, in blocks. */
    private static final float BRIM_HALF_WIDTH = 5f / 16f;

    @Test
    void theIceOverTheHatSpansTheBrim() {
        WitchModel ice = EncasedBodies.withBareRimHidden(new WitchModel(WitchModel.createBodyLayer().bakeRoot()));
        RecordingVertexConsumer drawn = new RecordingVertexConsumer();

        ice.getHead().getChild("hat").render(new PoseStack(), drawn, 0, 0);

        float minX = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float minZ = Float.POSITIVE_INFINITY;
        float maxZ = Float.NEGATIVE_INFINITY;
        for (RecordingVertexConsumer.Vertex vertex : drawn.vertices()) {
            minX = Math.min(minX, vertex.x());
            maxX = Math.max(maxX, vertex.x());
            minZ = Math.min(minZ, vertex.z());
            maxZ = Math.max(maxZ, vertex.z());
        }
        assertEquals(-BRIM_HALF_WIDTH, minX, EPSILON);
        assertEquals(BRIM_HALF_WIDTH, maxX, EPSILON);
        assertEquals(-BRIM_HALF_WIDTH, minZ, EPSILON);
        assertEquals(BRIM_HALF_WIDTH, maxZ, EPSILON);
    }
}
