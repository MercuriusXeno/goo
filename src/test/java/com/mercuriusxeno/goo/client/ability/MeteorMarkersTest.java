package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.client.RecordingVertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Meteo's marker is a splat lying flat on the ground where the blob
 * landed, never a shape floating over it (operator UAT 2026-11-06).
 * decision meteo-winds-up-then-drops-in-a-second-trailing-fire
 */
class MeteorMarkersTest {

    /** The center of the air cell the blob landed in, over ground at y 64. */
    private static final Vec3 TARGET = new Vec3(10.5, 64.5, -3.5);
    private static final double GROUND = 64;
    private static final double LIFT = 0.01;

    @Test
    void theSplatSitsOnTheGroundOfTheCellTheBlobLandedIn() {
        assertEquals(GROUND, MeteorMarkers.markerAt(TARGET).y, LIFT);
    }

    @Test
    void everyPointOfTheSplatLiesFlatOnTheGround() {
        RecordingVertexConsumer consumer = new RecordingVertexConsumer();

        MeteorMarkers.emitSplat(new PoseStack().last(), consumer, MeteorMarkers.markerAt(TARGET), TARGET, 0xFF8866FF);

        List<RecordingVertexConsumer.Vertex> vertices = consumer.vertices();
        assertTrue(!vertices.isEmpty(), "the splat draws");
        for (RecordingVertexConsumer.Vertex vertex : vertices) {
            assertEquals(GROUND, vertex.y(), LIFT, "flat on the ground");
        }
    }
}
