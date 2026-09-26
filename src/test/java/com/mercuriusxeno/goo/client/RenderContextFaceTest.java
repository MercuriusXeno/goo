package com.mercuriusxeno.goo.client;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Direction;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RenderContext's face emitter writes, for every direction, the corners,
 * UVs and normals the deleted GooRenderUtil face helpers wrote, in the same
 * order, so the one emitter keeps their winding (decision
 * render-context-is-the-one-emitter).
 */
class RenderContextFaceTest {

    private static final float X0 = 0.1f;
    private static final float X1 = 0.9f;
    private static final float Z0 = 0.2f;
    private static final float Z1 = 0.8f;
    private static final float Y0 = 0.3f;
    private static final float Y1 = 0.7f;
    private static final float U0 = 0.0f;
    private static final float V0 = 0.25f;
    private static final float U1 = 0.5f;
    private static final float V1 = 1.0f;
    private static final int COLOR = 0xC0336699;
    private static final int LIGHT = 0x00F000A0;

    private static final CuboidBounds BOX = new CuboidBounds(X0, X1, Z0, Z1, Y0, Y1);
    private static final GooRenderUtil.UvRect UV = new GooRenderUtil.UvRect(U0, V0, U1, V1);

    /**
     * One corner as the old helpers emitted it: position then UV.
     *
     * @param x the X position
     * @param y the Y position
     * @param z the Z position
     * @param u the texture U
     * @param v the texture V
     */
    private record Corner(float x, float y, float z, float u, float v) {}

    /** The corners GooRenderUtil's faceYUp .. faceZNorth emitted, transcribed in their order. */
    private static final Map<Direction, List<Corner>> OLD_CORNERS = Map.of(
            Direction.UP, List.of(
                    new Corner(X0, Y1, Z0, U0, V0), new Corner(X0, Y1, Z1, U0, V1),
                    new Corner(X1, Y1, Z1, U1, V1), new Corner(X1, Y1, Z0, U1, V0)),
            Direction.DOWN, List.of(
                    new Corner(X1, Y0, Z0, U1, V0), new Corner(X1, Y0, Z1, U1, V1),
                    new Corner(X0, Y0, Z1, U0, V1), new Corner(X0, Y0, Z0, U0, V0)),
            Direction.EAST, List.of(
                    new Corner(X1, Y1, Z1, U1, V0), new Corner(X1, Y0, Z1, U1, V1),
                    new Corner(X1, Y0, Z0, U0, V1), new Corner(X1, Y1, Z0, U0, V0)),
            Direction.WEST, List.of(
                    new Corner(X0, Y1, Z0, U1, V0), new Corner(X0, Y0, Z0, U1, V1),
                    new Corner(X0, Y0, Z1, U0, V1), new Corner(X0, Y1, Z1, U0, V0)),
            Direction.SOUTH, List.of(
                    new Corner(X0, Y1, Z1, U1, V0), new Corner(X0, Y0, Z1, U1, V1),
                    new Corner(X1, Y0, Z1, U0, V1), new Corner(X1, Y1, Z1, U0, V0)),
            Direction.NORTH, List.of(
                    new Corner(X1, Y1, Z0, U0, V0), new Corner(X1, Y0, Z0, U0, V1),
                    new Corner(X0, Y0, Z0, U1, V1), new Corner(X0, Y1, Z0, U1, V0)));

    private static List<RecordingVertexConsumer.Vertex> emit(Direction face) {
        RecordingVertexConsumer consumer = new RecordingVertexConsumer();
        new RenderContext(new PoseStack().last(), consumer, LIGHT, COLOR).emitFace(BOX, UV, face);
        return consumer.vertices();
    }

    @ParameterizedTest
    @EnumSource(Direction.class)
    void faceCornersAndUvsMatchTheOldHelpers(Direction face) {
        List<RecordingVertexConsumer.Vertex> emitted = emit(face);
        List<Corner> expected = OLD_CORNERS.get(face);

        assertEquals(expected.size(), emitted.size());
        for (int i = 0; i < expected.size(); i++) {
            RecordingVertexConsumer.Vertex vertex = emitted.get(i);
            assertEquals(expected.get(i),
                    new Corner(vertex.x(), vertex.y(), vertex.z(), vertex.u(), vertex.v()),
                    face + " corner " + i);
        }
    }

    @ParameterizedTest
    @EnumSource(Direction.class)
    void everyVertexCarriesTheFaceNormalColorAndLight(Direction face) {
        for (RecordingVertexConsumer.Vertex vertex : emit(face)) {
            assertEquals(face.getStepX(), vertex.nx());
            assertEquals(face.getStepY(), vertex.ny());
            assertEquals(face.getStepZ(), vertex.nz());
            assertEquals(COLOR, vertex.color());
            assertEquals(LIGHT & 0xFFFF, vertex.uv2U());
            assertEquals(LIGHT >>> 16, vertex.uv2V());
        }
    }

    @ParameterizedTest
    @EnumSource(Direction.class)
    void windingFacesOutward(Direction face) {
        List<RecordingVertexConsumer.Vertex> emitted = emit(face);
        RecordingVertexConsumer.Vertex a = emitted.get(0);
        RecordingVertexConsumer.Vertex b = emitted.get(1);
        RecordingVertexConsumer.Vertex c = emitted.get(2);
        float e0x = b.x() - a.x();
        float e0y = b.y() - a.y();
        float e0z = b.z() - a.z();
        float e1x = c.x() - a.x();
        float e1y = c.y() - a.y();
        float e1z = c.z() - a.z();
        float crossX = e0y * e1z - e0z * e1y;
        float crossY = e0z * e1x - e0x * e1z;
        float crossZ = e0x * e1y - e0y * e1x;
        float alongNormal = crossX * face.getStepX() + crossY * face.getStepY() + crossZ * face.getStepZ();

        assertTrue(alongNormal > 0f, face + " winds inward");
    }
}
