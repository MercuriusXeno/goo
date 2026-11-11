package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.outsider.OutsiderRooms;
import com.mercuriusxeno.goo.ability.outsider.PlatformSpiral;
import com.mercuriusxeno.goo.ability.outsider.RoomEdges;
import com.mercuriusxeno.goo.ability.quantum.Gate;
import com.mercuriusxeno.goo.ability.quantum.GatePassage;
import com.mercuriusxeno.goo.network.RoomEdgesSyncPayload;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the views a frame may draw through a room's edges: each edge, looked
 * into toward its partner, is a way a view draws, each is a gate the frame
 * draws a face for, and the frame's views hold the room's four edges beside a
 * portal pair's two and a hole's two
 * (decision outsider-room-wraps-and-repeats-at-the-platform-edge).
 */
class PortalViewTest {

    /** A portal pair's two mouths, a hole's two and a room's four edges. */
    private static final int VIEWS_A_ROOM_NEEDS = 8;
    private static final RoomEdges.Edges EDGES = RoomEdges.around(OutsiderRooms.voidstoneOf(5),
            PlatformSpiral.START_CELLS);

    @BeforeEach
    void standInTheRoom() {
        ClientRoomEdges.take(new RoomEdgesSyncPayload(EDGES.pairs()));
    }

    @AfterEach
    void leaveTheRoom() {
        ClientRoomEdges.take(new RoomEdgesSyncPayload(List.of()));
        GatePassage.setClientPairs(List.of());
    }

    @Test
    void eachEdgeLookedIntoTowardItsPartnerIsAWayAViewDraws() {
        List<List<Gate>> ways = PortalView.ways().stream().map(List::of).toList();

        for (List<Gate> way : List.of(List.of(EDGES.east(), EDGES.west()), List.of(EDGES.west(), EDGES.east()),
                List.of(EDGES.south(), EDGES.north()), List.of(EDGES.north(), EDGES.south()))) {
            assertTrue(ways.contains(way), "the ways should hold " + way.get(0).face() + " into its partner");
        }
    }

    @Test
    void theFrameDrawsAFaceForEachOfTheFourEdges() {
        List<Gate> drawn = PortalFaces.gatesIn(OutsiderRooms.POCKET).stream().map(PortalFaces.Drawn::gate).toList();

        assertEquals(List.of(EDGES.east(), EDGES.west(), EDGES.south(), EDGES.north()), drawn);
    }

    @Test
    void theFramesViewsHoldARoomsEdgesBesideAPortalPairAndAHole() {
        assertTrue(PortalView.MOST_VIEWS >= VIEWS_A_ROOM_NEEDS,
                "a frame should draw at least " + VIEWS_A_ROOM_NEEDS + " views, draws " + PortalView.MOST_VIEWS);
    }
}
