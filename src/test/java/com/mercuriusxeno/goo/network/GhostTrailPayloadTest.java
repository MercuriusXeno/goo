package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.type.GooTypes;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The ghost trail broadcast carries the entity, both ends of its jump, the
 * goo type and the life whole (decision ghost-trail-spans-the-blink).
 */
class GhostTrailPayloadTest {

    @Test
    void aTrailRoundTripsThroughTheStreamCodec() {
        GhostTrailPayload sent = new GhostTrailPayload(31337, new Vec3(-4.5, 70, 12.25), new Vec3(3.5, 70, 12.25),
                GooTypes.ENDER, 20);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());

        GhostTrailPayload.STREAM_CODEC.encode(buf, sent);

        assertEquals(sent, GhostTrailPayload.STREAM_CODEC.decode(buf));
    }
}
