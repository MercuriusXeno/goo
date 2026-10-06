package com.mercuriusxeno.goo.network;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The throw carries the exact point aimed at to the server, and a re-aimed
 * throw lands at the block holding the new point (decision aim-point-follows-the-cursor).
 */
class GooThrowPayloadTest {

    private static final Vec3 ORIGIN = new Vec3(0.4, 65.3, -0.2);

    @Test
    void theAimedPointRoundTripsThroughTheStreamCodec() {
        GooThrowPayload sent = new GooThrowPayload("goo:unstable", -1, new BlockPos(4, 63, 9), Direction.NORTH.ordinal(),
                false, "goo:unstable_explode", ORIGIN, new Vec3(4.3, 63.75, 9.0));
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());

        GooThrowPayload.STREAM_CODEC.encode(buf, sent);

        assertEquals(sent, GooThrowPayload.STREAM_CODEC.decode(buf));
    }

    @Test
    void aReAimedThrowLandsAtTheBlockHoldingThePointOnItsTopFace() {
        GooThrowPayload thrown = new GooThrowPayload("goo:unstable", -1, new BlockPos(4, 63, 9), Direction.NORTH.ordinal(),
                false, "goo:unstable_explode", ORIGIN, new Vec3(90, 120, 9));
        Vec3 capped = new Vec3(40.5, 100.25, 9.75);

        GooThrowPayload aimed = thrown.aimedAt(capped);

        assertEquals(new GooThrowPayload("goo:unstable", -1, new BlockPos(40, 100, 9), Direction.UP.ordinal(),
                false, "goo:unstable_explode", ORIGIN, capped), aimed);
    }
}
