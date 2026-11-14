package com.mercuriusxeno.goo.ability.zone;

import com.mercuriusxeno.goo.ability.quantum.Gate;
import com.mercuriusxeno.goo.ability.quantum.GatePassage;
import com.mercuriusxeno.goo.ability.quantum.PortalTransform;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.util.List;
import java.util.function.ToDoubleFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Covers a room's edges: four gates standing exactly the room's extent from
 * its center, even partway through a block, facing in, east paired with west
 * and south with north, each carrying a point across twice the extent and a
 * direction unchanged (decisions outsider-room-wraps-and-repeats-at-the-platform-edge
 * and grown-extent-is-the-one-wrap-and-build-boundary).
 */
class RoomEdgesTest {

    private static final BlockPos CENTER = new BlockPos(1_000_000, ZoneRooms.ROOM_FLOOR_Y, -2_000_000);
    /** The middle of the center voidstone, where the extent is measured from. */
    private static final double MIDDLE_X = CENTER.getX() + 0.5;
    private static final double MIDDLE_Z = CENTER.getZ() + 0.5;
    private static final double EXACT = 1.0E-9;
    private static final Vec3 HEADING = new Vec3(0.3, -0.2, 0.7);

    @ParameterizedTest(name = "extent {0}")
    @CsvSource({"1.5", "2.3", "3.85"})
    void eachEdgeStandsExactlyTheExtentFromTheCenterFacingIn(double extent) {
        RoomEdges.Edges edges = RoomEdges.around(CENTER, extent);

        assertPlane(edges.east(), Direction.WEST, MIDDLE_X + extent, Vec3::x);
        assertPlane(edges.west(), Direction.EAST, MIDDLE_X - extent, Vec3::x);
        assertPlane(edges.south(), Direction.NORTH, MIDDLE_Z + extent, Vec3::z);
        assertPlane(edges.north(), Direction.SOUTH, MIDDLE_Z - extent, Vec3::z);
    }

    @ParameterizedTest(name = "extent {0}")
    @CsvSource({"2.3", "3.85"})
    void aFractionalExtentStandsTheEdgesOffTheBlockFaces(double extent) {
        RoomEdges.Edges edges = RoomEdges.around(CENTER, extent);

        for (Gate edge : List.of(edges.east(), edges.west(), edges.south(), edges.north())) {
            double plane = edge.face().getAxis() == Direction.Axis.X ? edge.center().x() : edge.center().z();
            assertNotEquals(Math.floor(plane), plane, EXACT, "an edge partway through a block, at " + plane);
        }
    }

    @ParameterizedTest(name = "extent {0}")
    @CsvSource({"1.5", "2.3"})
    void eastPairsWithWestAndSouthWithNorth(double extent) {
        RoomEdges.Edges edges = RoomEdges.around(CENTER, extent);

        assertEquals(List.of(new GatePassage.Pair(edges.east(), edges.west()),
                new GatePassage.Pair(edges.south(), edges.north())), edges.pairs());
    }

    @ParameterizedTest(name = "extent {0}")
    @CsvSource({"1.5", "2.3"})
    void crossingEastCarriesAPointTwiceTheExtentWestAndKeepsItsHeading(double extent) {
        RoomEdges.Edges edges = RoomEdges.around(CENTER, extent);
        PortalTransform through = PortalTransform.between(edges.east(), edges.west());
        Vec3 point = new Vec3(CENTER.getX() + 1.2, ZoneRooms.ROOM_FLOOR_Y + 1.6, CENTER.getZ() - 0.4);

        assertNear(point.subtract(2 * extent, 0, 0), through.point(point), "point carried twice the extent");
        assertNear(HEADING, through.direction(HEADING), "heading kept");
    }

    @ParameterizedTest(name = "extent {0}")
    @CsvSource({"1.5", "2.3"})
    void crossingSouthCarriesAPointTwiceTheExtentNorth(double extent) {
        RoomEdges.Edges edges = RoomEdges.around(CENTER, extent);
        Vec3 point = new Vec3(CENTER.getX() - 0.7, ZoneRooms.ROOM_FLOOR_Y + 1.6, CENTER.getZ() + 1.1);

        assertNear(point.subtract(0, 0, 2 * extent),
                PortalTransform.between(edges.south(), edges.north()).point(point), "point carried twice the extent");
    }

    private static void assertNear(Vec3 expected, Vec3 actual, String what) {
        assertEquals(0, expected.distanceTo(actual), EXACT, () -> what + ": expected " + expected + ", got " + actual);
    }

    private static void assertPlane(Gate gate, Direction face, double plane, ToDoubleFunction<Vec3> axis) {
        assertEquals(face, gate.face(), "face");
        assertEquals(Gate.Mask.RECTANGLE, gate.mask(), "mask");
        assertEquals(ZoneRooms.POCKET, gate.dimension(), "dimension");
        assertEquals(plane, axis.applyAsDouble(gate.center()), EXACT, "plane");
    }
}
