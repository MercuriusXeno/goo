package com.mercuriusxeno.goo.network;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A charged release reaches the server with the ticks it was held and the
 * press's scatter seed, so the knives fly along the lines the client drew
 * (decision knives-aims-a-spread-fan-not-a-blob-line).
 */
class GooChargePayloadTest {

    @Test
    void aReleaseCarriesItsHoldAndItsScatterSeed() {
        GooChargePayload sent = new GooChargePayload("goo:crystal", "goo:crystal_knives", 17, -6_917_529_027_641_081_856L);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());

        GooChargePayload.STREAM_CODEC.encode(buf, sent);

        assertEquals(sent, GooChargePayload.STREAM_CODEC.decode(buf));
    }
}
