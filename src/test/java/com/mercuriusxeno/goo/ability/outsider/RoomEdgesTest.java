package com.mercuriusxeno.goo.ability.outsider;

import com.mercuriusxeno.goo.ability.quantum.Gate;
import com.mercuriusxeno.goo.ability.quantum.GatePassage;
import com.mercuriusxeno.goo.ability.quantum.PortalTransform;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Covers a room's edges: four gates on the outer faces of the outermost grown
 * cells, facing in, east paired with west and south with north, each carrying
 * a point across the square's width and a direction unchanged
 * (decision outsider-room-wraps-and-repeats-at-the-platform-edge).
 */
class RoomEdgesTest {

    private static final BlockPos CENTER = new BlockPos(1_000_000, OutsiderRooms.ROOM_FLOOR_Y, -2_000_000);
    private static final double EXACT = 1.0E-9;
    private static final Vec3 HEADING = new Vec3(0.3, -0.2, 0.7);

    @ParameterizedTest(name = "{0} cells, {1} across")
    @CsvSource({"9, 3", "25, 5"})
    void eachEdgeStandsOnTheOuterFaceOfTheOutermostCellsFacingIn(int cells, int across) {
        int ring = (across - 1) / 2;
        RoomEdges.Edges edges = RoomEdges.around(CENTER, cells);

        assertPlane(edges.east(), Direction.WEST, CENTER.getX() + ring + 1, Vec3::x);
        assertPlane(edges.west(), Direction.EAST, CENTER.getX() - ring, Vec3::x);
        assertPlane(edges.south(), Direction.NORTH, CENTER.getZ() + ring + 1, Vec3::z);
        assertPlane(edges.north(), Direction.SOUTH, CENTER.getZ() - ring, Vec3::z);
    }

    @ParameterizedTest(name = "{0} cells")
    @CsvSource({"9", "25"})
    void eastPairsWithWestAndSouthWithNorth(int cells) {
        RoomEdges.Edges edges = RoomEdges.around(CENTER, cells);

        assertEquals(List.of(new GatePassage.Pair(edges.east(), edges.west()),
                new GatePassage.Pair(edges.south(), edges.north())), edges.pairs());
    }

    @ParameterizedTest(name = "{0} cells, {1} across")
    @CsvSource({"9, 3", "25, 5"})
    void crossingEastCarriesAPointTheSquaresWidthWestAndKeepsItsHeading(int cells, int across) {
        RoomEdges.Edges edges = RoomEdges.around(CENTER, cells);
        PortalTransform through = PortalTransform.between(edges.east(), edges.west());
        Vec3 point = new Vec3(CENTER.getX() + 1.2, OutsiderRooms.ROOM_FLOOR_Y + 1.6, CENTER.getZ() - 0.4);

        assertNear(point.subtract(across, 0, 0), through.point(point), "point carried by the square's width");
        assertNear(HEADING, through.direction(HEADING), "heading kept");
    }

    @ParameterizedTest(name = "{0} cells, {1} across")
    @CsvSource({"9, 3", "25, 5"})
    void crossingSouthCarriesAPointTheSquaresWidthNorth(int cells, int across) {
        RoomEdges.Edges edges = RoomEdges.around(CENTER, cells);
        Vec3 point = new Vec3(CENTER.getX() - 0.7, OutsiderRooms.ROOM_FLOOR_Y + 1.6, CENTER.getZ() + 1.1);

        assertNear(point.subtract(0, 0, across), PortalTransform.between(edges.south(), edges.north()).point(point),
                "point carried by the square's width");
    }

    private static void assertNear(Vec3 expected, Vec3 actual, String what) {
        assertEquals(0, expected.distanceTo(actual), EXACT, () -> what + ": expected " + expected + ", got " + actual);
    }

    private static void assertPlane(Gate gate, Direction face, double plane,
            java.util.function.ToDoubleFunction<Vec3> axis) {
        assertEquals(face, gate.face(), "face");
        assertEquals(Gate.Mask.RECTANGLE, gate.mask(), "mask");
        assertEquals(OutsiderRooms.POCKET, gate.dimension(), "dimension");
        assertEquals(plane, axis.applyAsDouble(gate.center()), EXACT, "plane");
    }
}
