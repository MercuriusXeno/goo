package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.outsider.OutsiderRooms;
import com.mercuriusxeno.goo.ability.outsider.RoomEdges;
import com.mercuriusxeno.goo.ability.outsider.RoomExtent;
import com.mercuriusxeno.goo.ability.quantum.Gate;
import com.mercuriusxeno.goo.ability.quantum.GatePassage;
import com.mercuriusxeno.goo.client.outsider.RoomCopies;
import com.mercuriusxeno.goo.client.outsider.SkyResources;
import com.mercuriusxeno.goo.network.RoomEdgesSyncPayload;
import net.minecraft.world.level.material.FogType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the views a frame may draw in an Outsider room: a new room at a
 * 12-chunk render distance tiles more copies than a frame's views could draw,
 * while the views look into at most the room's four edges (decision
 * room-tiles-to-render-distance-held-to-the-picture), and a view of the
 * pocket runs the sky pass its star sky draws through (decision
 * outsider-room-draws-a-skybox-of-stars).
 */
class PortalViewTest {

    /** A new room, 9 blocks across. */
    private static final RoomEdges.Edges NEW_ROOM = RoomEdges.around(OutsiderRooms.voidstoneOf(5), RoomExtent.LAID);
    private static final int TWELVE_CHUNKS = 12;
    /** A room's four edges, each looked into toward its partner. */
    private static final int ROOM_EDGE_WAYS = 4;

    @AfterEach
    void leaveTheRoom() {
        ClientRoomEdges.take(new RoomEdgesSyncPayload(List.of()));
        GatePassage.setClientPairs(List.of());
    }

    @Test
    void aNewRoomAtTwelveChunksTilesPastWhatTheViewsCouldDraw() {
        ClientRoomEdges.take(new RoomEdgesSyncPayload(NEW_ROOM.pairs()));
        long edgeWays = PortalView.ways().stream().filter(way -> way[0].mask() == Gate.Mask.RECTANGLE).count();
        int copies = RoomCopies.offsets(ClientRoomEdges.bounds().orElseThrow(),
                RoomCopies.reachOf(TWELVE_CHUNKS)).size();

        assertTrue(edgeWays <= ROOM_EDGE_WAYS, "the views should look into at most the room's four edges, look into "
                + edgeWays);
        assertTrue(copies > PortalView.MOST_VIEWS, "the room should tile " + copies + " copies, past the "
                + PortalView.MOST_VIEWS + " views a frame draws");
    }

    @Test
    void aViewOfThePocketsSkyRunsItsSkyPass() {
        assertTrue(PortalView.skyDraws(SkyResources.skyboxOf(SkyResources.POCKET), FogType.NONE, false),
                "a view of the pocket should run the sky pass its stars draw through");
    }
}
