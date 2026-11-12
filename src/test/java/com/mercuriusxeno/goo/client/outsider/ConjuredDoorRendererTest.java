package com.mercuriusxeno.goo.client.outsider;

import com.mercuriusxeno.goo.client.GooRenderTypes;
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
 * Covers the conjured door's submit: its rip draws under the end portal
 * render type as whole quads of position-only vertices, the doorway's span,
 * then its rim draws bright in the starfield's teal and violet, each quad
 * seen from either side (decisions crack-and-doorway-wear-the-end-portal-shader,
 * conjured-door-opens-as-a-jagged-rip, rip-wears-a-bright-rim-on-its-torn-edge).
 */
class ConjuredDoorRendererTest {

    private static final Vec3 FEET = new Vec3(3.5, 64, 8.5);
    private static final float EXACT = 1.0E-5f;
    private static final ClientConjuredDoors.Drawn FULL_DOOR = new ClientConjuredDoors.Drawn(FEET, 0f, 1);

    /** One submit's render type and the vertices its renderer wrote. */
    private record Submit(RenderType type, RecordingVertexConsumer vertices) {
    }

    @Test
    void theDoorSubmitsTheStarfieldThenTheRim() {
        List<Submit> submits = submit(FULL_DOOR);

        assertEquals(2, submits.size(), "the starfield and the rim");
        assertEquals(RenderTypes.endPortal(), submits.get(0).type(), "the starfield first");
        assertEquals(ConjuredDoorRenderer.RIM_RENDER_TYPE, submits.get(1).type(), "the rim second");
    }

    @Test
    void theDoorEmitsPositionsAloneSpanningTheDoorway() {
        List<RecordingVertexConsumer.Vertex> vertices = starfieldOf(FULL_DOOR);

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

    @Test
    void theRipEmitsWholeQuadsEachBothWaysRound() {
        List<RecordingVertexConsumer.Vertex> vertices = starfieldOf(FULL_DOOR);

        assertTrue(vertices.size() > 8, "the rip draws more than one quad: " + vertices.size());
        assertEachQuadBothWaysRound(vertices);
    }

    @Test
    void theRimEmitsWholeQuadsEachBothWaysRound() {
        List<RecordingVertexConsumer.Vertex> vertices = rimOf(FULL_DOOR);

        assertFalse(vertices.isEmpty(), "the rim emitted vertices");
        assertEachQuadBothWaysRound(vertices);
    }

    @Test
    void theRimWearsTheStarfieldsTealAndViolet() {
        List<RecordingVertexConsumer.Vertex> vertices = rimOf(FULL_DOOR);

        assertFalse(vertices.isEmpty(), "the rim emitted vertices");
        for (RecordingVertexConsumer.Vertex vertex : vertices) {
            for (int shift : new int[] {16, 8, 0}) {
                int channel = (vertex.color() >> shift) & 0xFF;
                int teal = (DoorRim.TEAL >> shift) & 0xFF;
                int violet = (DoorRim.VIOLET >> shift) & 0xFF;
                assertTrue(channel >= Math.min(teal, violet) && channel <= Math.max(teal, violet),
                        "channel at shift " + shift + " lies between teal and violet: "
                                + Integer.toHexString(vertex.color()));
            }
        }
        assertTrue(vertices.stream().anyMatch(vertex -> vertex.color() == DoorRim.TEAL), "teal on the rim");
        assertTrue(vertices.stream().anyMatch(vertex -> vertex.color() == DoorRim.VIOLET), "violet on the rim");
    }

    private static void assertEachQuadBothWaysRound(List<RecordingVertexConsumer.Vertex> vertices) {
        assertEquals(0, vertices.size() % 8, "whole quads, each emitted twice: " + vertices.size());
        for (int start = 0; start < vertices.size(); start += 8) {
            for (int corner = 0; corner < 4; corner++) {
                RecordingVertexConsumer.Vertex ahead = vertices.get(start + corner);
                RecordingVertexConsumer.Vertex back = vertices.get(start + 7 - corner);
                assertEquals(ahead.x(), back.x(), EXACT, "quad at " + start + " corner " + corner + " x");
                assertEquals(ahead.y(), back.y(), EXACT, "quad at " + start + " corner " + corner + " y");
                assertEquals(ahead.z(), back.z(), EXACT, "quad at " + start + " corner " + corner + " z");
            }
        }
    }

    private static List<RecordingVertexConsumer.Vertex> starfieldOf(ClientConjuredDoors.Drawn door) {
        return submittedUnder(door, RenderTypes.endPortal());
    }

    private static List<RecordingVertexConsumer.Vertex> rimOf(ClientConjuredDoors.Drawn door) {
        return submittedUnder(door, ConjuredDoorRenderer.RIM_RENDER_TYPE);
    }

    private static List<RecordingVertexConsumer.Vertex> submittedUnder(ClientConjuredDoors.Drawn door,
                                                                     RenderType type) {
        return submit(door).stream().filter(submit -> submit.type() == type).findFirst()
                .map(submit -> submit.vertices().vertices()).orElse(List.of());
    }

    /** Submits one door seen from its own feet and records each submit it handed the collector. */
    private static List<Submit> submit(ClientConjuredDoors.Drawn door) {
        List<Submit> submits = new ArrayList<>();
        SubmitNodeCollector collector = mock(SubmitNodeCollector.class);
        doAnswer(call -> {
            PoseStack at = call.getArgument(0);
            RecordingVertexConsumer vertices = new RecordingVertexConsumer();
            SubmitNodeCollector.CustomGeometryRenderer renderer = call.getArgument(2);
            renderer.render(at.last(), vertices);
            submits.add(new Submit(call.getArgument(1), vertices));
            return null;
        }).when(collector).submitCustomGeometry(any(PoseStack.class), any(RenderType.class), any());

        ConjuredDoorRenderer.submitDoor(collector, new PoseStack(), door, FEET);
        return submits;
    }
}
