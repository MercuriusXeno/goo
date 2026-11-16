package com.mercuriusxeno.goo.network;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A charged release reaches the server with the ticks it was held, which
 * the knives' fixed pattern reads its share of a full charge from
 * (decision knives-land-in-staggered-rows-with-no-randomness).
 */
class GooChargePayloadTest {

    @Test
    void aReleaseCarriesItsHold() {
        GooChargePayload sent = new GooChargePayload("goo:crystal", "goo:crystal_knives", 17);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());

        GooChargePayload.STREAM_CODEC.encode(buf, sent);

        assertEquals(sent, GooChargePayload.STREAM_CODEC.decode(buf));
    }
}
