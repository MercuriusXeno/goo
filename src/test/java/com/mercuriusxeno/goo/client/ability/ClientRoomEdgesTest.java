package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.outsider.OutsiderRooms;
import com.mercuriusxeno.goo.ability.outsider.RoomEdges;
import com.mercuriusxeno.goo.ability.quantum.Gate;
import com.mercuriusxeno.goo.ability.quantum.GatePassage;
import com.mercuriusxeno.goo.network.RoomEdgesSyncPayload;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Covers the client taking its room's edges: the passage's client pairs hold
 * the four edges the server sent, paired, once the payload is handled, each
 * plane exactly where the server stood it, partway through a block or not
 * (decisions outsider-room-wraps-and-repeats-at-the-platform-edge and
 * grown-extent-is-the-one-wrap-and-build-boundary).
 */
class ClientRoomEdgesTest {

    /** An extent no float carries exactly, so a lossy codec would move the planes. */
    private static final double PARTWAY = 2.3456789012345;
    private static final BlockPos CENTER = OutsiderRooms.voidstoneOf(3);
    /** The rounding a gate's centre picks up from its box a million blocks out; the codec itself adds none. */
    private static final double PLANE_MATH = 1.0E-9;

    @AfterEach
    void forgetTheEdges() {
        ClientRoomEdges.take(new RoomEdgesSyncPayload(List.of()));
        GatePassage.setClientPairs(List.of());
    }

    @Test
    void handlingTheSyncHoldsTheSentPlanesToFullPrecision() {
        RoomEdgesSyncPayload sent = new RoomEdgesSyncPayload(RoomEdges.around(CENTER, PARTWAY).pairs());
        ByteBuf wire = Unpooled.buffer();
        RoomEdgesSyncPayload.STREAM_CODEC.encode(wire, sent);

        ClientRoomEdges.take(RoomEdgesSyncPayload.STREAM_CODEC.decode(wire));

        List<GatePassage.Pair> held = GatePassage.clientPairs();
        assertEquals(sent.edges(), held);
        Gate east = held.getFirst().one();
        assertEquals(CENTER.getX() + 0.5 + PARTWAY, east.center().x(), PLANE_MATH, "the east plane held");
    }
}
