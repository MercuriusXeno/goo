package com.mercuriusxeno.goo.client.zone;

import com.mercuriusxeno.goo.client.RecordingVertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

/**
 * Covers the conjured door's submit: the outline its two shares give draws
 * under the end portal render type with position-only vertices, seen from
 * either side (decisions crack-and-doorway-wear-the-end-portal-shader,
 * door-unzips-from-a-starfield-pixel-then-widens-into-you).
 */
class ConjuredDoorRendererTest {

    private static final Vec3 FEET = new Vec3(3.5, 64, 8.5);
    private static final float EXACT = 1.0E-5f;
    private static final float HALF_PIXEL = 1f / 32;

    private record Submitted(List<RenderType> types, RecordingVertexConsumer vertices) {
    }

    @Test
    void aHalfUnzippedSlashDrawsFromTheHeadHalfwayDownUnderTheEndPortal() {
        Submitted submitted = submit(new ClientConjuredDoors.Drawn(FEET, 0f, 0.5, 0));

        assertEquals(List.of(RenderTypes.endPortal()), submitted.types(), "one end portal submit");
        List<RecordingVertexConsumer.Vertex> vertices = submitted.vertices().vertices();
        assertFalse(vertices.isEmpty(), "the slash emitted vertices");
        for (RecordingVertexConsumer.Vertex vertex : vertices) {
            assertTrue(vertex.y() >= 1 - EXACT && vertex.y() <= 2 + EXACT, "between one and two up: " + vertex.y());
            assertTrue(Math.abs(vertex.x()) <= HALF_PIXEL + EXACT, "within half a pixel of the centerline: "
                    + vertex.x());
        }
    }

    @Test
    void aFullDoorEmitsPositionsAloneSpanningTheDoorway() {
        Submitted submitted = submit(new ClientConjuredDoors.Drawn(FEET, 0f, 1, 1));

        assertEquals(List.of(RenderTypes.endPortal()), submitted.types(), "one end portal submit");
        List<RecordingVertexConsumer.Vertex> vertices = submitted.vertices().vertices();
        assertFalse(vertices.isEmpty(), "the door emitted vertices");
        double lowest = Double.MAX_VALUE;
        double highest = -Double.MAX_VALUE;
        double leftmost = Double.MAX_VALUE;
        double rightmost = -Double.MAX_VALUE;
        for (RecordingVertexConsumer.Vertex vertex : vertices) {
            assertEquals(0, vertex.color(), "no color written");
            assertEquals(0f, vertex.u(), EXACT, "no texture written");
            assertEquals(0f, vertex.nz(), EXACT, "no normal written");
            lowest = Math.min(lowest, vertex.y());
            highest = Math.max(highest, vertex.y());
            leftmost = Math.min(leftmost, vertex.x());
            rightmost = Math.max(rightmost, vertex.x());
        }
        assertEquals(0, lowest, EXACT, "standing on the feet");
        assertEquals(2, highest, EXACT, "two tall");
        assertEquals(-0.5, leftmost, EXACT, "half a block to the left");
        assertEquals(0.5, rightmost, EXACT, "half a block to the right");
    }

    /** Submits one door seen from its own feet and records what it handed the collector. */
    private static Submitted submit(ClientConjuredDoors.Drawn door) {
        List<RenderType> types = new ArrayList<>();
        RecordingVertexConsumer vertices = new RecordingVertexConsumer();
        SubmitNodeCollector collector = mock(SubmitNodeCollector.class);
        doAnswer(call -> {
            PoseStack at = call.getArgument(0);
            types.add(call.getArgument(1));
            SubmitNodeCollector.CustomGeometryRenderer renderer = call.getArgument(2);
            renderer.render(at.last(), vertices);
            return null;
        }).when(collector).submitCustomGeometry(any(PoseStack.class), any(RenderType.class), any());

        ConjuredDoorRenderer.submitDoor(collector, new PoseStack(), door, FEET);
        return new Submitted(types, vertices);
    }
}
