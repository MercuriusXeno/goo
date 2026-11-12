package com.mercuriusxeno.goo.client.outsider;

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
 * Covers the conjured door's submit: its crack draws under the end portal
 * render type with position-only vertices, the doorway's span, seen from
 * either side (decision crack-and-doorway-wear-the-end-portal-shader).
 */
class ConjuredDoorRendererTest {

    private static final Vec3 FEET = new Vec3(3.5, 64, 8.5);
    private static final float EXACT = 1.0E-5f;

    private record Submitted(List<RenderType> types, RecordingVertexConsumer vertices) {
    }

    @Test
    void theDoorSubmitsUnderTheEndPortalRenderType() {
        Submitted submitted = submit(new ClientConjuredDoors.Drawn(FEET, 0f, 1));

        assertFalse(submitted.types().isEmpty(), "the door submitted geometry");
        for (RenderType type : submitted.types()) {
            assertEquals(RenderTypes.endPortal(), type);
        }
    }

    @Test
    void theDoorEmitsPositionsAloneSpanningTheDoorway() {
        List<RecordingVertexConsumer.Vertex> vertices = submit(new ClientConjuredDoors.Drawn(FEET, 0f, 1))
                .vertices().vertices();

        assertFalse(vertices.isEmpty(), "the door emitted vertices");
        double lowest = Double.MAX_VALUE;
        double highest = -Double.MAX_VALUE;
        for (RecordingVertexConsumer.Vertex vertex : vertices) {
            assertEquals(0, vertex.color(), "no color written");
            assertEquals(0f, vertex.u(), EXACT, "no texture written");
            assertEquals(0f, vertex.nz(), EXACT, "no normal written");
            assertTrue(Math.abs(vertex.x()) <= 0.5f + EXACT, "within the doorway's width: " + vertex.x());
            lowest = Math.min(lowest, vertex.y());
            highest = Math.max(highest, vertex.y());
        }
        assertEquals(0, lowest, EXACT, "standing on the feet");
        assertEquals(2, highest, EXACT, "two tall");
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
