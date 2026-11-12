package com.mercuriusxeno.goo.client.ber;

import com.mercuriusxeno.goo.client.RecordingVertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

/**
 * Covers the room door's draw: each of its two cells submits a quad under
 * the end portal render type with position-only vertices, both ways round,
 * and the two together stand the one wide, two tall doorway in the plane
 * through the cells' middle running east to west
 * (decision room-door-wears-the-end-portal-starfield).
 */
class DimensionDoorRendererTest {

    private static final float EXACT = 1.0E-5f;
    private static final float MIDDLE = 0.5f;
    private static final int QUAD = 4;

    private record Submitted(List<RenderType> types, List<RecordingVertexConsumer.Vertex> vertices) {
    }

    @Test
    void bothCellsSubmitUnderTheEndPortalRenderType() {
        Submitted lower = submitCell(0);
        Submitted upper = submitCell(1);

        assertFalse(lower.types().isEmpty() || upper.types().isEmpty(), "each cell submits geometry");
        for (RenderType type : List.of(lower.types(), upper.types()).stream().flatMap(List::stream).toList()) {
            assertEquals(RenderTypes.endPortal(), type);
        }
    }

    @Test
    void theTwoCellsStandOneWideTwoTallInTheMiddlePlaneSeenFromEitherSide() {
        List<RecordingVertexConsumer.Vertex> lower = submitCell(0).vertices();
        List<RecordingVertexConsumer.Vertex> upper = submitCell(1).vertices();
        List<RecordingVertexConsumer.Vertex> both = new ArrayList<>(lower);
        both.addAll(upper);

        for (RecordingVertexConsumer.Vertex vertex : both) {
            assertEquals(0, vertex.color(), "no color written");
            assertEquals(0f, vertex.u(), EXACT, "no texture written");
            assertEquals(0f, vertex.nx() + vertex.ny() + vertex.nz(), EXACT, "no normal written");
            assertEquals(MIDDLE, vertex.z(), EXACT, "in the plane through the cells' middle, east to west");
        }
        assertEquals(1, span(both, true), EXACT, "one block wide");
        assertEquals(2, span(both, false), EXACT, "two blocks tall from the lower cell's foot");
        assertEquals(0, both.stream().mapToDouble(RecordingVertexConsumer.Vertex::y).min().orElseThrow(), EXACT,
                "standing on the lower cell's foot");
        assertBothWindings(lower);
        assertBothWindings(upper);
    }

    /** A cell's quad, then the same corners in the reverse order, so the face shows from either side. */
    private static void assertBothWindings(List<RecordingVertexConsumer.Vertex> vertices) {
        assertEquals(2 * QUAD, vertices.size(), "one quad each way round");
        for (int index = 0; index < QUAD; index++) {
            RecordingVertexConsumer.Vertex forward = vertices.get(index);
            RecordingVertexConsumer.Vertex back = vertices.get(2 * QUAD - 1 - index);
            assertEquals(forward.x(), back.x(), EXACT);
            assertEquals(forward.y(), back.y(), EXACT);
            assertEquals(forward.z(), back.z(), EXACT);
        }
    }

    private static double span(List<RecordingVertexConsumer.Vertex> vertices, boolean across) {
        double low = Double.MAX_VALUE;
        double high = -Double.MAX_VALUE;
        for (RecordingVertexConsumer.Vertex vertex : vertices) {
            double at = across ? vertex.x() : vertex.y();
            low = Math.min(low, at);
            high = Math.max(high, at);
        }
        return high - low;
    }

    /** Submits one doorway cell standing a number of blocks above the lower cell, as the dispatcher poses it. */
    private static Submitted submitCell(int above) {
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
        PoseStack pose = new PoseStack();
        pose.translate(0, above, 0);

        new DimensionDoorRenderer(null).submit(new BlockEntityRenderState(), pose, collector,
                mock(CameraRenderState.class));
        return new Submitted(types, vertices.vertices());
    }
}
