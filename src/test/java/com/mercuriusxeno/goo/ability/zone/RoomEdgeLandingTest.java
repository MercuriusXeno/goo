package com.mercuriusxeno.goo.ability.zone;

import com.mercuriusxeno.goo.ability.quantum.GatePassage;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers a walker crossing a room's edge: their feet, standing on the floor's
 * top as their eye crosses, land on the floor's top past the partner edge,
 * not a hair below it, where the floor block would hold them inside it and
 * let them drop through (decision outsider-room-wraps-and-repeats-at-the-platform-edge).
 */
class RoomEdgeLandingTest {

    private static final BlockPos CENTER = ZoneRooms.voidstoneOf(6);
    private static final double FLOOR_TOP = ZoneRooms.ROOM_FLOOR_Y + 1;
    private static final double EYE = 1.62;
    private static final double TALL = 1.8;

    @ParameterizedTest(name = "extent {0}")
    @ValueSource(doubles = {4.5, 7.3, 40.7, 300.2})
    void aWalkerOnTheFloorLandsOnTheFloorCrossingEachEdge(double extent) {
        RoomEdges.Edges edges = RoomEdges.around(CENTER, extent);
        RoomExtent.Planes planes = RoomExtent.planesAround(CENTER, extent);
        double step = 0.2;
        assertLandsOnTheFloor(edges.east(), edges.west(), new Vec3(planes.east() - 0.05, FLOOR_TOP, CENTER.getZ() + 0.37),
                new Vec3(step, 0, 0));
        assertLandsOnTheFloor(edges.west(), edges.east(), new Vec3(planes.west() + 0.05, FLOOR_TOP, CENTER.getZ() + 0.37),
                new Vec3(-step, 0, 0));
        assertLandsOnTheFloor(edges.south(), edges.north(), new Vec3(CENTER.getX() + 0.37, FLOOR_TOP,
                planes.south() - 0.05), new Vec3(0, 0, step));
        assertLandsOnTheFloor(edges.north(), edges.south(), new Vec3(CENTER.getX() + 0.37, FLOOR_TOP,
                planes.north() + 0.05), new Vec3(0, 0, -step));
    }

    private static void assertLandsOnTheFloor(com.mercuriusxeno.goo.ability.quantum.Gate near,
            com.mercuriusxeno.goo.ability.quantum.Gate far, Vec3 feetBefore, Vec3 motion) {
        Vec3 feet = feetBefore.add(motion);
        Optional<GatePassage.Held> held = GatePassage.heldBehind(near, feet, EYE, TALL, motion);
        assertTrue(held.isPresent(), "the walker's eye should be held behind " + near.face());
        GatePassage.Landing landing = GatePassage.landingThrough(near, far, held.get(), TALL, motion, 0F, 0F);
        assertEquals(FLOOR_TOP, landing.feet().y(), 0.0,
                "feet crossing " + near.face() + " should land on the floor's top, not inside the floor");
    }
}
