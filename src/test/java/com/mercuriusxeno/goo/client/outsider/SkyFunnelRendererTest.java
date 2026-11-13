package com.mercuriusxeno.goo.client.outsider;

import com.mercuriusxeno.goo.ability.outsider.RoomExtent;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.client.RecordingVertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

/**
 * Covers the sky funnel's draw: one funnel a frame, under goo's copy of the
 * End portal shader with its stars fixed to the surface at a set size in
 * blocks, round the pillar by its own width and flat across the ceiling,
 * fading with the log of distance, every quad both ways round, at the room's
 * own doorway however many copies stand
 * (decision sky-funnel-points-down-to-the-doorway-from-any-distance).
 */
class SkyFunnelRendererTest {

    private static final float EXACT = 1.0E-3f;
    /** How near a starfield coordinate reads to its expected value, the vertex read back in floats. */
    private static final double COORDINATE_EXACT = 1.0E-2;
    /** How near a brightness reads to the log fade, two steps of its byte. */
    private static final double BRIGHTNESS_EXACT = 2.0 / 255;
    /** How near the mean of the funnel's vertices stands to the center, its far rings drawn in floats. */
    private static final double CENTERED = 1.0E-1;
    private static final double FULL_TURN = Math.PI * 2;
    private static final int RED_SHIFT = 16;
    private static final int BYTE = 0xFF;
    private static final int QUAD = 4;
    private static final double HALF_BLOCK = 0.5;
    private static final RoomExtent.Bounds ROOM = new RoomExtent.Bounds(1_000, -3_000, 40.5);
    /** The camera stands by the doorway, so no vertex is drawn nearer than its true place. */
    private static final Vec3 BY_THE_DOOR = new Vec3(1_003.5, 65, -2_996.5);
    private static final double FAR_ENOUGH_FOR_ALL = 1.0E6;
    private static final double SIXTEEN_CHUNKS = 256;

    private record Submitted(List<RenderType> types, List<RecordingVertexConsumer.Vertex> vertices) {
    }

    @Test
    void theFunnelDrawsWithGoosCopyOfTheEndPortalShader() {
        assertEquals(List.of(GooRenderTypes.SKY_FUNNEL_TYPE), submit().types(), "goo's funnel render type");
    }

    @Test
    void theStarsRunRoundThePillarAndFlatAcrossTheCeilingAtTheirBlocksPerRepeat() {
        List<RecordingVertexConsumer.Vertex> vertices = submit().vertices();
        assertFalse(vertices.isEmpty(), "the funnel writes vertices");
        boolean pillarSeen = false;
        boolean ceilingSeen = false;
        for (RecordingVertexConsumer.Vertex vertex : vertices) {
            double east = vertex.x() + BY_THE_DOOR.x() - (ROOM.centerX() + HALF_BLOCK);
            double south = vertex.z() + BY_THE_DOOR.z() - (ROOM.centerZ() + HALF_BLOCK);
            double height = vertex.y() + BY_THE_DOOR.y() - SkyFunnel.spoutHeight();
            if (SkyFunnel.wrapsAsPillar(height)) {
                pillarSeen = true;
                assertEquals(height / SkyFunnel.PILLAR_BLOCKS_PER_REPEAT, vertex.v(), COORDINATE_EXACT,
                        "up the pillar by height");
                double around = Math.atan2(south, east) / FULL_TURN;
                double apart = Math.abs(around - vertex.u()) % 1;
                assertTrue(Math.min(apart, 1 - apart) < COORDINATE_EXACT, "round the pillar by its own width");
            } else {
                ceilingSeen = true;
                assertEquals(east / SkyFunnel.CEILING_BLOCKS_PER_REPEAT, vertex.u(), COORDINATE_EXACT,
                        "across the ceiling by east");
                assertEquals(south / SkyFunnel.CEILING_BLOCKS_PER_REPEAT, vertex.v(), COORDINATE_EXACT,
                        "across the ceiling by south");
            }
        }
        assertTrue(pillarSeen && ceilingSeen, "the funnel draws both its pillar and its ceiling");
    }

    @Test
    void theBrightnessFadesWithTheLogOfDistance() {
        List<RecordingVertexConsumer.Vertex> vertices = submit().vertices();
        double nearest = vertices.stream().mapToDouble(SkyFunnelRendererTest::distanceOf).min().orElseThrow();
        double farthest = vertices.stream().mapToDouble(SkyFunnelRendererTest::distanceOf).max().orElseThrow();
        for (RecordingVertexConsumer.Vertex vertex : vertices) {
            double nearToFar = Math.log(distanceOf(vertex) / nearest) / Math.log(farthest / nearest);
            double expected = 1 - (1 - SkyFunnel.FARTHEST_BRIGHTNESS) * nearToFar;
            assertEquals(expected, brightnessOf(vertex) / (double) BYTE, BRIGHTNESS_EXACT,
                    "brightness by the log of distance");
        }
        RecordingVertexConsumer.Vertex nearestVertex = vertices.stream()
                .min(Comparator.comparingDouble(SkyFunnelRendererTest::distanceOf)).orElseThrow();
        RecordingVertexConsumer.Vertex farthestVertex = vertices.stream()
                .max(Comparator.comparingDouble(SkyFunnelRendererTest::distanceOf)).orElseThrow();
        assertEquals(vertices.stream().mapToInt(SkyFunnelRendererTest::brightnessOf).max().orElseThrow(),
                brightnessOf(nearestVertex), "the nearest vertex brightest");
        assertEquals(vertices.stream().mapToInt(SkyFunnelRendererTest::brightnessOf).min().orElseThrow(),
                brightnessOf(farthestVertex), "the farthest vertex dimmest");
    }

    @Test
    void everyQuadDrawsBothWaysRound() {
        List<RecordingVertexConsumer.Vertex> vertices = submit().vertices();
        assertFalse(vertices.isEmpty(), "the funnel writes vertices");
        assertEquals(0, vertices.size() % (2 * QUAD), "whole quads, each both ways round");
        for (int start = 0; start < vertices.size(); start += 2 * QUAD) {
            for (int index = 0; index < QUAD; index++) {
                RecordingVertexConsumer.Vertex forward = vertices.get(start + index);
                RecordingVertexConsumer.Vertex back = vertices.get(start + 2 * QUAD - 1 - index);
                assertEquals(forward.x(), back.x(), EXACT, "the reverse winding repeats the corners");
                assertEquals(forward.y(), back.y(), EXACT, "the reverse winding repeats the corners");
                assertEquals(forward.z(), back.z(), EXACT, "the reverse winding repeats the corners");
                assertEquals(forward.u(), back.u(), EXACT, "the reverse winding repeats the stars");
                assertEquals(forward.v(), back.v(), EXACT, "the reverse winding repeats the stars");
                assertEquals(forward.color(), back.color(), "the reverse winding repeats the brightness");
            }
        }
    }

    @Test
    void oneFunnelDrawsAtTheRoomsOwnDoorwayWhereTheRoomHasManyCopies() {
        List<RoomCopies.Offset> copies = RoomCopies.offsets(ROOM, SIXTEEN_CHUNKS);
        assertTrue(copies.size() > 1, "the frame draws more than one copy of the room");

        Submitted submitted = submit();

        assertEquals(1, submitted.types().size(), "one funnel submitted");
        assertEquals(SkyFunnel.quads(ROOM).size() * 2 * QUAD, submitted.vertices().size(),
                "one funnel's quads, each both ways round");
        double east = submitted.vertices().stream().mapToDouble(vertex -> vertex.x() + BY_THE_DOOR.x())
                .average().orElseThrow();
        double south = submitted.vertices().stream().mapToDouble(vertex -> vertex.z() + BY_THE_DOOR.z())
                .average().orElseThrow();
        assertEquals(ROOM.centerX() + HALF_BLOCK, east, CENTERED, "at the room's own center, the zero copy offset");
        assertEquals(ROOM.centerZ() + HALF_BLOCK, south, CENTERED, "at the room's own center, the zero copy offset");
    }

    private static double distanceOf(RecordingVertexConsumer.Vertex vertex) {
        return Math.sqrt(vertex.x() * vertex.x() + vertex.y() * vertex.y() + vertex.z() * vertex.z());
    }

    private static int brightnessOf(RecordingVertexConsumer.Vertex vertex) {
        return vertex.color() >> RED_SHIFT & BYTE;
    }

    private static Submitted submit() {
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

        SkyFunnelRenderer.submit(collector, new PoseStack(), ROOM, BY_THE_DOOR, FAR_ENOUGH_FOR_ALL);
        return new Submitted(types, vertices.vertices());
    }
}
