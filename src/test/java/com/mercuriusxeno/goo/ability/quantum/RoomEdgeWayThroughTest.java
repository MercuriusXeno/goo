package com.mercuriusxeno.goo.ability.quantum;

import com.mercuriusxeno.goo.ability.outsider.OutsiderRooms;
import com.mercuriusxeno.goo.ability.outsider.RoomEdges;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the server finding the way through a gate a client says it
 * crossed: a room's edge the client crossed a tick before the room grew a
 * sliver still leads through the server's edge now, where refusing it set the
 * player back across the seam; a portal still matches only itself
 * (decision outsider-room-wraps-and-repeats-at-the-platform-edge).
 */
class RoomEdgeWayThroughTest {

    private static final BlockPos CENTER = OutsiderRooms.voidstoneOf(6);

    @Test
    void anEdgeTheRoomGrewPastStillLeadsThroughTheServersEdgeNow() {
        List<GatePassage.Pair> server = RoomEdges.around(CENTER, 167.35).pairs();
        RoomEdges.Edges crossed = RoomEdges.around(CENTER, 167.30);

        Optional<GatePassage.Pair> way = GatePassage.wayThrough(server, crossed.east(), OutsiderRooms.POCKET);

        assertTrue(way.isPresent(), "the edge crossed a tick before the room grew should still lead through");
        assertEquals(RoomEdges.around(CENTER, 167.35).east(), way.get().one(), "the server's east edge now");
        assertEquals(RoomEdges.around(CENTER, 167.35).west(), way.get().other(), "out by its west edge");
    }

    @Test
    void anEdgeFacingTheOtherWayIsNotTheSameEdge() {
        List<GatePassage.Pair> server = RoomEdges.around(CENTER, 167.35).pairs();
        RoomEdges.Edges crossed = RoomEdges.around(CENTER, 167.30);

        Optional<GatePassage.Pair> way = GatePassage.wayThrough(server, crossed.south(), OutsiderRooms.POCKET);

        assertEquals(RoomEdges.around(CENTER, 167.35).south(), way.orElseThrow().one(),
                "the south edge matches the south edge, not the east one");
    }

    @Test
    void aPortalMatchesOnlyItself() {
        Gate portal = new Gate(OutsiderRooms.POCKET, new AABB(0, 64, 0, 1, 66, 1), Direction.EAST,
                Direction.UP.getUnitVec3());
        Gate partner = new Gate(OutsiderRooms.POCKET, new AABB(10, 64, 0, 11, 66, 1), Direction.WEST,
                Direction.UP.getUnitVec3());
        Gate nudged = new Gate(OutsiderRooms.POCKET, new AABB(0.5, 64, 0, 1.5, 66, 1), Direction.EAST,
                Direction.UP.getUnitVec3());

        assertTrue(GatePassage.wayThrough(List.of(new GatePassage.Pair(portal, partner)), nudged,
                OutsiderRooms.POCKET).isEmpty(), "a portal half a block off is another gate");
    }
}
