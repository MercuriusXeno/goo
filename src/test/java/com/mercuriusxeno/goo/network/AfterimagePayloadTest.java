package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.type.GooTypes;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The afterimage broadcast carries any entity, point, goo type and life
 * whole, so any step can leave an echo of any living entity
 * (decision afterimage-is-one-shared-effect).
 */
class AfterimagePayloadTest {

    @Test
    void anEchoOfAnyEntityAndGooTypeRoundTripsThroughTheStreamCodec() {
        AfterimagePayload sent = new AfterimagePayload(98765, new Vec3(-12.25, 70.5, 3.125), GooTypes.HEX, 40);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());

        AfterimagePayload.STREAM_CODEC.encode(buf, sent);

        assertEquals(sent, AfterimagePayload.STREAM_CODEC.decode(buf));
    }
}
