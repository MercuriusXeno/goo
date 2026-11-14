package com.mercuriusxeno.goo.client.zone;

import com.mercuriusxeno.goo.ability.zone.RoomExtent;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.client.RecordingVertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

/**
 * Covers the sky columns' draw: a column over the room's own doorway and
 * over every copy's, each the room's own moved by its copy's offset, each
 * drawing only the sides the camera sees at full brightness under goo's copy
 * of the End portal shader, its stars read by view direction so no texture
 * coordinate is written, culled by its own box, and standing where it stood
 * as the camera crosses the room's edge
 * (decision sky-funnel-points-down-to-the-doorway-from-any-distance).
 */
class SkyColumnRendererTest {

    private static final float EXACT = 1.0E-3f;
    private static final int QUAD = 4;
    private static final int OPAQUE_WHITE = 0xFFFFFFFF;
    private static final double HALF_BLOCK = 0.5;
    private static final RoomExtent.Bounds ROOM = new RoomExtent.Bounds(1_000, -3_000, 40.5);
    private static final double WIDTH = 2 * ROOM.extent();
    private static final double AXIS_X = ROOM.centerX() + HALF_BLOCK;
    private static final double AXIS_Z = ROOM.centerZ() + HALF_BLOCK;
    /** The camera stands by the doorway, so no column's foot is drawn nearer than its true place. */
    private static final Vec3 BY_THE_DOOR = new Vec3(AXIS_X + 3, 65, AXIS_Z + 3);
    private static final double FAR_ENOUGH_FOR_ALL = 1.0E7;
    /** A far plane whose reach the column tops pass, the vanilla one at sixteen chunks. */
    private static final double SIXTEEN_CHUNK_FAR_PLANE = 1024;
    private static final double SIXTEEN_CHUNKS = 256;
    private static final double ROOM_BOTTOM = 60;
    private static final double ROOM_TOP = 80;
    private static final List<RoomCopies.Offset> COPIES = RoomCopies.offsets(ROOM, SIXTEEN_CHUNKS);
    private static final RoomCopies.Offset OWN = new RoomCopies.Offset(0, 0);
    private static final Predicate<AABB> SEES_ALL = box -> true;

    private record Submitted(List<RenderType> types, List<RecordingVertexConsumer.Vertex> vertices) {

        List<List<RecordingVertexConsumer.Vertex>> quads() {
            assertEquals(0, vertices.size() % QUAD, "whole quads");
            List<List<RecordingVertexConsumer.Vertex>> quads = new ArrayList<>();
            for (int start = 0; start < vertices.size(); start += QUAD) {
                quads.add(vertices.subList(start, start + QUAD));
            }
            return quads;
        }
    }

    @Test
    void theColumnsDrawAWindowIntoSpaceThroughGoosEndPortalShader() {
        Submitted submitted = submit(COPIES, BY_THE_DOOR, FAR_ENOUGH_FOR_ALL, SEES_ALL);

        assertEquals(List.of(GooRenderTypes.SKY_FUNNEL_TYPE), submitted.types(), "goo's funnel render type");
        assertFalse(submitted.vertices().isEmpty(), "the columns write vertices");
        for (RecordingVertexConsumer.Vertex vertex : submitted.vertices()) {
            assertEquals(0f, vertex.u(), 0f, "no texture coordinate written, the stars read the view direction");
            assertEquals(0f, vertex.v(), 0f, "no texture coordinate written, the stars read the view direction");
        }
    }

    @Test
    void aColumnDrawsOverTheRoomsOwnDoorwayAndOverEveryCopysDoorway() {
        assertTrue(COPIES.size() > 1, "the room has more than one copy");

        Map<RoomCopies.Offset, List<List<RecordingVertexConsumer.Vertex>>> columns =
                columnsOf(submit(COPIES, BY_THE_DOOR, FAR_ENOUGH_FOR_ALL, SEES_ALL), BY_THE_DOOR);

        assertEquals(COPIES.size() + 1, columns.size(), "a column for each copy and one for the room's own");
        assertTrue(columns.containsKey(OWN), "a column over the room's own doorway, the zero offset");
        for (RoomCopies.Offset copy : COPIES) {
            assertTrue(columns.containsKey(copy), "a column over the doorway of copy " + copy);
        }
        columns.forEach((offset, quads) -> assertTrue(quads.size() <= 2,
                "one column a doorway, at most two sides seen from outside its footprint, at " + offset));
    }

    @Test
    void aCopysColumnIsTheRoomsOwnMovedByItsOffsetInsideTheFarthestDepth() {
        assertCopysColumnIsTheRoomsOwnMoved(BY_THE_DOOR, FAR_ENOUGH_FOR_ALL);
    }

    @Test
    void aCopysColumnIsTheRoomsOwnMovedByItsOffsetFarPastTheFarthestDepth() {
        assertCopysColumnIsTheRoomsOwnMoved(BY_THE_DOOR.add(SIXTEEN_CHUNK_FAR_PLANE * 10, 0, 0),
                SIXTEEN_CHUNK_FAR_PLANE);
    }

    @Test
    void aCopysColumnDrawsWhereItsRoomStandsOutOfView() {
        RoomCopies.Offset copy = COPIES.getFirst();
        AABB columnBox = SkyColumn.boxOf(ROOM).move(copy.east(), 0, copy.south());
        AABB roomBox = RoomCopyDraws.boxOf(ROOM, ROOM_BOTTOM, ROOM_TOP).move(copy.east(), 0, copy.south());
        Predicate<AABB> seesOnlyTheSkyOverTheCopy = box -> sameBox(box, columnBox);
        assertFalse(seesOnlyTheSkyOverTheCopy.test(roomBox), "the view does not see the copy's room");

        Map<RoomCopies.Offset, List<List<RecordingVertexConsumer.Vertex>>> columns =
                columnsOf(submit(COPIES, BY_THE_DOOR, FAR_ENOUGH_FOR_ALL, seesOnlyTheSkyOverTheCopy), BY_THE_DOOR);

        assertTrue(columns.containsKey(copy), "the copy's column draws where its room stands out of view");
    }

    @Test
    void aColumnSeenCornerOnDrawsTheTwoSidesTurnedTowardTheCamera() {
        Vec3 cornerOn = new Vec3(AXIS_X + 5, 65, AXIS_Z + 7);
        Submitted submitted = submit(List.of(), cornerOn, FAR_ENOUGH_FOR_ALL, SEES_ALL);

        assertSidesTurnedToward(submitted, cornerOn, 2);
    }

    @Test
    void aColumnSeenFaceOnDrawsTheOneSideTurnedTowardTheCamera() {
        Vec3 faceOn = new Vec3(AXIS_X + 0.2, 65, AXIS_Z + 7);
        Submitted submitted = submit(List.of(), faceOn, FAR_ENOUGH_FOR_ALL, SEES_ALL);

        assertSidesTurnedToward(submitted, faceOn, 1);
    }

    @Test
    void crossingTheRoomsEdgeLeavesEveryColumnWhereItStood() {
        Vec3 eastEdge = new Vec3(AXIS_X + ROOM.extent() - 0.01, 65, AXIS_Z + 3);
        Vec3 carriedWest = eastEdge.subtract(WIDTH, 0, 0);
        Map<RoomCopies.Offset, List<List<RecordingVertexConsumer.Vertex>>> before =
                columnsOf(submit(COPIES, eastEdge, FAR_ENOUGH_FOR_ALL, SEES_ALL), eastEdge);
        Map<RoomCopies.Offset, List<List<RecordingVertexConsumer.Vertex>>> after =
                columnsOf(submit(COPIES, carriedWest, FAR_ENOUGH_FOR_ALL, SEES_ALL), carriedWest);
        double farthestWest = COPIES.stream().mapToDouble(RoomCopies.Offset::east).min().orElseThrow();
        double farthestEast = COPIES.stream().mapToDouble(RoomCopies.Offset::east).max().orElseThrow();

        before.forEach((offset, quads) -> {
            if (offset.east() > farthestWest) {
                assertSameQuads(quads, after.get(new RoomCopies.Offset(offset.east() - WIDTH, offset.south())),
                        "the column at " + offset + " stands where it stood after the crossing");
            }
        });
        after.forEach((offset, quads) -> {
            if (offset.east() < farthestEast) {
                assertSameQuads(before.get(new RoomCopies.Offset(offset.east() + WIDTH, offset.south())), quads,
                        "the column at " + offset + " stood there before the crossing");
            }
        });
    }

    private static void assertCopysColumnIsTheRoomsOwnMoved(Vec3 camera, double farthest) {
        Submitted submitted = submit(COPIES, camera, farthest, SEES_ALL);
        assertEquals(List.of(GooRenderTypes.SKY_FUNNEL_TYPE), submitted.types(), "goo's funnel render type");
        List<Vec3> expected = new ArrayList<>();
        for (RoomCopies.Offset offset : withOwn(COPIES)) {
            Vec3 shift = new Vec3(offset.east(), 0, offset.south());
            for (List<Vec3> quad : SkyColumn.quads(ROOM, camera.subtract(shift))) {
                for (Vec3 corner : quad) {
                    expected.add(SkyColumn.fromCamera(camera, corner.add(shift), farthest));
                }
            }
        }
        List<RecordingVertexConsumer.Vertex> drawn = submitted.vertices();
        assertEquals(expected.size(), drawn.size(), "every column's corners, the room's own moved by each offset");
        for (int index = 0; index < drawn.size(); index++) {
            assertEquals(expected.get(index).x(), drawn.get(index).x(), EXACT, "moved by the offset, on its ray");
            assertEquals(expected.get(index).y(), drawn.get(index).y(), EXACT, "moved by the offset, on its ray");
            assertEquals(expected.get(index).z(), drawn.get(index).z(), EXACT, "moved by the offset, on its ray");
            assertTrue(Math.sqrt(drawn.get(index).x() * drawn.get(index).x()
                    + drawn.get(index).y() * drawn.get(index).y()
                    + drawn.get(index).z() * drawn.get(index).z()) < farthest, "inside the farthest drawn depth");
        }
    }

    private static void assertSidesTurnedToward(Submitted submitted, Vec3 camera, int sides) {
        List<List<RecordingVertexConsumer.Vertex>> quads = submitted.quads();
        assertEquals(sides, quads.size(), "the sides the camera sees, one quad each");
        assertEquals(sides * QUAD, submitted.vertices().size(), "four vertices a quad");
        for (List<RecordingVertexConsumer.Vertex> quad : quads) {
            Vec3 first = placeOf(quad.get(0));
            Vec3 normal = placeOf(quad.get(1)).subtract(first).cross(placeOf(quad.get(2)).subtract(placeOf(quad.get(1))));
            assertTrue(normal.dot(first.scale(-1)) > 0, "wound to face the camera");
            Vec3 outward = new Vec3(normal.x(), 0, normal.z()).normalize();
            for (RecordingVertexConsumer.Vertex vertex : quad) {
                Vec3 truePlace = placeOf(vertex).add(camera);
                double out = (truePlace.x() - AXIS_X) * outward.x() + (truePlace.z() - AXIS_Z) * outward.z();
                if (placeOf(vertex).length() < FAR_ENOUGH_FOR_ALL * SkyColumn.REACH_SHARE) {
                    assertEquals(HALF_BLOCK, out, EXACT, "on the side of the footprint turned toward the camera");
                }
                assertEquals(OPAQUE_WHITE, vertex.color(), "full brightness");
            }
        }
    }

    private static void assertSameQuads(List<List<RecordingVertexConsumer.Vertex>> expected,
            List<List<RecordingVertexConsumer.Vertex>> actual, String message) {
        assertNotNull(expected, message);
        assertNotNull(actual, message);
        assertEquals(expected.size(), actual.size(), message);
        for (int quad = 0; quad < expected.size(); quad++) {
            for (int corner = 0; corner < QUAD; corner++) {
                RecordingVertexConsumer.Vertex was = expected.get(quad).get(corner);
                RecordingVertexConsumer.Vertex is = actual.get(quad).get(corner);
                assertEquals(was.x(), is.x(), EXACT, message);
                assertEquals(was.y(), is.y(), EXACT, message);
                assertEquals(was.z(), is.z(), EXACT, message);
            }
        }
    }

    /**
     * Sorts the drawn quads by the doorway each stands over, read from its
     * foot corners, which stand inside the reach at their true places.
     */
    private static Map<RoomCopies.Offset, List<List<RecordingVertexConsumer.Vertex>>> columnsOf(Submitted submitted,
            Vec3 camera) {
        Map<RoomCopies.Offset, List<List<RecordingVertexConsumer.Vertex>>> columns = new LinkedHashMap<>();
        for (List<RecordingVertexConsumer.Vertex> quad : submitted.quads()) {
            RoomCopies.Offset offset = null;
            for (RecordingVertexConsumer.Vertex vertex : quad) {
                Vec3 truePlace = placeOf(vertex).add(camera);
                if (Math.abs(truePlace.y() - SkyColumn.spoutHeight()) > EXACT) {
                    continue;
                }
                RoomCopies.Offset at = new RoomCopies.Offset(Math.round((truePlace.x() - AXIS_X) / WIDTH) * WIDTH,
                        Math.round((truePlace.z() - AXIS_Z) / WIDTH) * WIDTH);
                assertEquals(HALF_BLOCK, Math.abs(truePlace.x() - AXIS_X - at.east()), EXACT,
                        "a foot corner on the doorway's footprint");
                assertEquals(HALF_BLOCK, Math.abs(truePlace.z() - AXIS_Z - at.south()), EXACT,
                        "a foot corner on the doorway's footprint");
                assertTrue(offset == null || offset.equals(at), "one quad stands over one doorway");
                offset = at;
            }
            assertNotNull(offset, "every quad stands on a doorway's top");
            columns.computeIfAbsent(offset, key -> new ArrayList<>()).add(quad);
        }
        return columns;
    }

    private static List<RoomCopies.Offset> withOwn(List<RoomCopies.Offset> copies) {
        List<RoomCopies.Offset> all = new ArrayList<>(copies.size() + 1);
        all.add(OWN);
        all.addAll(copies);
        return all;
    }

    private static boolean sameBox(AABB box, AABB other) {
        return Math.abs(box.minX - other.minX) < EXACT && Math.abs(box.minY - other.minY) < EXACT
                && Math.abs(box.minZ - other.minZ) < EXACT && Math.abs(box.maxX - other.maxX) < EXACT
                && Math.abs(box.maxY - other.maxY) < EXACT && Math.abs(box.maxZ - other.maxZ) < EXACT;
    }

    private static Vec3 placeOf(RecordingVertexConsumer.Vertex vertex) {
        return new Vec3(vertex.x(), vertex.y(), vertex.z());
    }

    private static Submitted submit(List<RoomCopies.Offset> copies, Vec3 camera, double farthest,
            Predicate<AABB> seen) {
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

        SkyColumnRenderer.submit(collector, new PoseStack(), ROOM, copies, camera, farthest, seen);
        return new Submitted(types, vertices.vertices());
    }
}
