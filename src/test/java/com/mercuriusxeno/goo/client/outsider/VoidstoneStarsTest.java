package com.mercuriusxeno.goo.client.outsider;

import com.mercuriusxeno.goo.ability.outsider.OutsiderRooms;
import com.mercuriusxeno.goo.ability.outsider.RoomEdges;
import com.mercuriusxeno.goo.ability.outsider.RoomExtent;
import com.mercuriusxeno.goo.ability.quantum.GatePassage;
import com.mercuriusxeno.goo.client.ability.ClientRoomEdges;
import com.mercuriusxeno.goo.network.RoomEdgesSyncPayload;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers which voidstone draws its stars: a block wholly inside the room's
 * boundary does, the block the boundary cuts does not, the same blocks the
 * server's build test takes and refuses, and a block gains its stars on the
 * edge update that clears its far face
 * (decision boundary-cut-voidstone-draws-without-its-stars).
 */
class VoidstoneStarsTest {

    private static final BlockPos CENTER = OutsiderRooms.voidstoneOf(6);
    /** An extent ending three tenths of the way into the blocks two out. */
    private static final double PARTWAY = 2.3;
    /** The same room grown past the far face of the blocks two out. */
    private static final double CLEARED = 2.6;

    @AfterEach
    void leaveTheRoom() {
        ClientRoomEdges.take(new RoomEdgesSyncPayload(List.of()));
        GatePassage.setClientPairs(List.of());
    }

    @ParameterizedTest(name = "toward ({0}, {1})")
    @CsvSource({"1, 0", "-1, 0", "0, 1", "0, -1"})
    void theCutBlockDrawsNoStarsAndItsInnerNeighbourDoes(int east, int south) {
        standIn(PARTWAY);

        assertTrue(starredAt(east, south), "the block wholly inside should draw its stars");
        assertFalse(starredAt(2 * east, 2 * south), "the block the boundary cuts should draw none");
    }

    @ParameterizedTest(name = "toward ({0}, {1})")
    @CsvSource({"1, 0", "-1, 0", "0, 1", "0, -1"})
    void theStarsFollowTheServersBuildTestAcrossTheBoundary(int east, int south) {
        standIn(PARTWAY);

        for (int out = 0; out <= 2; out++) {
            assertEquals(RoomExtent.holdsWholeBlock(PARTWAY, out * east, out * south),
                    starredAt(out * east, out * south), "the block " + out + " out should star as it builds");
        }
    }

    @Test
    void aBlockGainsItsStarsOnTheUpdateThatClearsItsFarFace() {
        standIn(PARTWAY);
        assertFalse(starredAt(2, 0), "the block two out should draw no stars while the boundary cuts it");

        standIn(CLEARED);
        assertTrue(starredAt(2, 0), "the block two out should draw its stars once the boundary clears it");
    }

    private static void standIn(double extent) {
        ClientRoomEdges.take(new RoomEdgesSyncPayload(RoomEdges.around(CENTER, extent).pairs()));
    }

    private static boolean starredAt(int east, int south) {
        return VoidstoneStars.starred(CENTER.getX() + east, CENTER.getZ() + south);
    }
}
