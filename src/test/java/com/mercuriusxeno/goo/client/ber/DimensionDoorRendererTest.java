package com.mercuriusxeno.goo.client.ber;

import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.client.RecordingVertexConsumer;
import com.mercuriusxeno.goo.client.outsider.SkyFunnel;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

/**
 * Covers the room door's draw: each of its two cells draws a full cube of
 * the sky funnel's starfield, every face both ways round, its stars fixed to
 * the face at the pillar's blocks per repeat, so the doorway reads as one
 * material with the funnel rising from its top
 * (decision sky-funnel-points-down-to-the-doorway-from-any-distance).
 */
class DimensionDoorRendererTest {

    private static final float EXACT = 1.0E-5f;
    private static final int QUAD = 4;
    private static final int FACES = 6;
    private static final int AXES = 3;
    private static final int OPAQUE_WHITE = 0xFFFFFFFF;
    /** The doorway's lower cell, as OutsiderRooms stands it on the center voidstone. */
    private static final BlockPos LOWER_CELL = new BlockPos(1_000, 64, -3_000);

    private record Submitted(List<RenderType> types, List<RecordingVertexConsumer.Vertex> vertices) {
    }

    @Test
    void bothCellsDrawWithTheSkyFunnelsShader() {
        for (BlockPos cell : List.of(LOWER_CELL, LOWER_CELL.above())) {
            Submitted submitted = submitCell(cell);
            assertFalse(submitted.types().isEmpty(), "each cell submits geometry");
            submitted.types().forEach(type -> assertEquals(GooRenderTypes.SKY_FUNNEL_TYPE, type));
        }
    }

    @Test
    void eachCellDrawsTheCubesSixFacesBothWaysRound() {
        for (BlockPos cell : List.of(LOWER_CELL, LOWER_CELL.above())) {
            List<RecordingVertexConsumer.Vertex> vertices = submitCell(cell).vertices();
            assertEquals(FACES * 2 * QUAD, vertices.size(), "six faces, each both ways round");
            Set<String> faces = new HashSet<>();
            for (int start = 0; start < vertices.size(); start += 2 * QUAD) {
                List<RecordingVertexConsumer.Vertex> face = vertices.subList(start, start + 2 * QUAD);
                faces.add(faceOf(face));
                for (int index = 0; index < QUAD; index++) {
                    RecordingVertexConsumer.Vertex forward = face.get(index);
                    RecordingVertexConsumer.Vertex back = face.get(2 * QUAD - 1 - index);
                    assertEquals(forward.x(), back.x(), EXACT, "the reverse winding repeats the corners");
                    assertEquals(forward.y(), back.y(), EXACT, "the reverse winding repeats the corners");
                    assertEquals(forward.z(), back.z(), EXACT, "the reverse winding repeats the corners");
                }
            }
            assertEquals(FACES, faces.size(), "every face of the cube");
        }
    }

    @Test
    void theStarsSitOnEachFaceByPlaceAtThePillarsBlocksPerRepeat() {
        for (BlockPos cell : List.of(LOWER_CELL, LOWER_CELL.above())) {
            List<RecordingVertexConsumer.Vertex> vertices = submitCell(cell).vertices();
            for (int start = 0; start < vertices.size(); start += 2 * QUAD) {
                List<RecordingVertexConsumer.Vertex> face = vertices.subList(start, start + 2 * QUAD);
                int across = constantAxisOf(face);
                for (RecordingVertexConsumer.Vertex vertex : face) {
                    double height = cell.getY() + vertex.y() - SkyFunnel.spoutHeight();
                    double expectedU = across == 0 ? vertex.z() : vertex.x();
                    double expectedV = across == 1 ? vertex.z() : height;
                    assertEquals(expectedU / SkyFunnel.PILLAR_BLOCKS_PER_REPEAT, vertex.u(), EXACT,
                            "across the face by place");
                    assertEquals(expectedV / SkyFunnel.PILLAR_BLOCKS_PER_REPEAT, vertex.v(), EXACT,
                            "up the face by height, flat faces by place");
                    assertEquals(OPAQUE_WHITE, vertex.color(), "full brightness");
                }
            }
        }
    }

    /** The axis a face's corners all share, 0 for east, 1 for up, 2 for south. */
    private static int constantAxisOf(List<RecordingVertexConsumer.Vertex> face) {
        for (int axis = 0; axis < AXES; axis++) {
            float first = coordinateOf(face.getFirst(), axis);
            boolean shared = true;
            for (RecordingVertexConsumer.Vertex vertex : face) {
                shared &= Math.abs(coordinateOf(vertex, axis) - first) < EXACT;
            }
            if (shared) {
                return axis;
            }
        }
        throw new AssertionError("a face's corners share one axis");
    }

    private static String faceOf(List<RecordingVertexConsumer.Vertex> face) {
        int axis = constantAxisOf(face);
        float side = coordinateOf(face.getFirst(), axis);
        assertTrue(Math.abs(side) < EXACT || Math.abs(side - 1) < EXACT, "on the cube's own side");
        return axis + ":" + Math.round(side);
    }

    private static float coordinateOf(RecordingVertexConsumer.Vertex vertex, int axis) {
        return switch (axis) {
            case 0 -> vertex.x();
            case 1 -> vertex.y();
            default -> vertex.z();
        };
    }

    /** Submits one doorway cell, posed at its own corner as the dispatcher poses it. */
    private static Submitted submitCell(BlockPos cell) {
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
        BlockEntityRenderState state = new BlockEntityRenderState();
        state.blockPos = cell;

        new DimensionDoorRenderer(null).submit(state, new PoseStack(), collector, mock(CameraRenderState.class));
        return new Submitted(types, vertices.vertices());
    }
}
