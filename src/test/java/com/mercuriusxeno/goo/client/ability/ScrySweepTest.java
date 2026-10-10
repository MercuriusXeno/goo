package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.client.RecordingVertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * ScrySweep's reveal traces a block's voxel shape: a revealed side is drawn
 * on each of the shape's boxes, at the box's own bounds rather than the cube's.
 * decision scry-sphere-reveals-faces-and-glistens-mobs
 */
class ScrySweepTest {

    private static final AABB BOTTOM_SLAB = new AABB(0, 0, 0, 1, 0.5, 1);
    private static final AABB FENCE_POST = new AABB(0.375, 0, 0.375, 0.625, 1.5, 0.625);
    private static final float TOLERANCE = 0.01f;

    @Test
    void aSlabsTopShowsAtItsHalfHeight() {
        List<RecordingVertexConsumer.Vertex> quad = emit(BOTTOM_SLAB, Direction.UP);

        assertEquals(4, quad.size());
        for (RecordingVertexConsumer.Vertex v : quad) {
            assertEquals(0.5f, v.y(), TOLERANCE);
        }
    }

    @Test
    void aFencePostsSideSpansThePostNotTheCube() {
        List<RecordingVertexConsumer.Vertex> quad = emit(FENCE_POST, Direction.EAST);

        for (RecordingVertexConsumer.Vertex v : quad) {
            assertEquals(0.625f, v.x(), TOLERANCE);
            assertEquals(true, v.z() >= 0.375f - TOLERANCE && v.z() <= 0.625f + TOLERANCE, "z within the post");
        }
        assertEquals(1.5f, quad.stream().map(RecordingVertexConsumer.Vertex::y).max(Float::compare).orElseThrow(),
                TOLERANCE);
    }

    private static List<RecordingVertexConsumer.Vertex> emit(AABB box, Direction side) {
        RecordingVertexConsumer consumer = new RecordingVertexConsumer();
        ScrySweep.emitBoxFace(new PoseStack().last(), consumer, box, side, 0xFFFFE628);
        return consumer.vertices();
    }
}
