package com.mercuriusxeno.goo.ability.quantum;

import com.mercuriusxeno.goo.ability.zone.RoomEdges;
import com.mercuriusxeno.goo.ability.zone.RoomExtent;
import com.mercuriusxeno.goo.ability.zone.ZoneRooms;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Covers the floor under a room's edge: the voidstone the edge's column
 * stands over holds a player walking across the seam, rather than giving way
 * as a portal's wall does to a body passing into it, where the operator fell
 * through the floor at the seam (decision outsider-room-wraps-and-repeats-at-the-platform-edge).
 */
class RoomEdgeFloorHoldsTest {

    private static final BlockPos CENTER = ZoneRooms.voidstoneOf(6);

    @ParameterizedTest(name = "extent {0}")
    @ValueSource(doubles = {4.5, 12.97, 40.3})
    void theFloorUnderEachEdgeHoldsAWalker(double extent) {
        RoomEdges.Edges edges = RoomEdges.around(CENTER, extent);
        RoomExtent.Planes planes = RoomExtent.planesAround(CENTER, extent);
        Player walker = mock(Player.class);
        when(walker.getDeltaMovement()).thenReturn(new Vec3(0.2, 0, 0));
        int floor = ZoneRooms.ROOM_FLOOR_Y;
        int row = CENTER.getZ() + 1;
        int column = CENTER.getX() + 1;
        assertHolds(edges.east(), BlockPos.containing(planes.east() + 0.5, floor, row), walker);
        assertHolds(edges.west(), BlockPos.containing(planes.west() - 0.5, floor, row), walker);
        assertHolds(edges.south(), BlockPos.containing(column, floor, planes.south() + 0.5), walker);
        assertHolds(edges.north(), BlockPos.containing(column, floor, planes.north() - 0.5), walker);
    }

    private static void assertHolds(Gate edge, BlockPos floorBlock, Player walker) {
        assertFalse(GatePassage.givesWay(edge, floorBlock, walker),
                "the floor at " + floorBlock + " under the " + edge.face() + "-facing edge should hold the walker");
    }
}
