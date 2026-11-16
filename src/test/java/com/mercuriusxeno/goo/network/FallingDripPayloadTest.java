package com.mercuriusxeno.goo.network;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The payload a starting fall sends its chunk's watchers keeps every field
 * through its codec: the start point, the landing surface, the fall's ticks
 * and the goo's two colours (decision goo-moving-downward-falls-as-drips).
 */
class FallingDripPayloadTest {

    @Test
    void theFallsPayloadKeepsEveryFieldThroughItsCodec() {
        FallingDripPayload fall = new FallingDripPayload(new Vec3(12.5, 70.25, -3.5), 66.0667, 23, 0x51A03E,
                0xC9FECE);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());

        FallingDripPayload.STREAM_CODEC.encode(buf, fall);

        assertEquals(fall, FallingDripPayload.STREAM_CODEC.decode(buf));
    }
}
