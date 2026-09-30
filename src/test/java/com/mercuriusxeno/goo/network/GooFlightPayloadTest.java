package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.Delivery;
import com.mercuriusxeno.goo.ability.DeliveryKind;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The flight broadcast carries the thrown ability's delivery whole, so the
 * client renders the flight without a registry (decision delivery-block-in-ability-json).
 */
class GooFlightPayloadTest {

    @Test
    void deliveryRoundTripsThroughTheStreamCodec() {
        Delivery delivery = new Delivery(DeliveryKind.STREAM, 3.5, 6, 30, 10, false);
        GooFlightPayload sent = new GooFlightPayload(1, 2, 3, "goo:blaze", -1, new BlockPos(4, 5, 6), 1,
                7, false, "goo:blaze_fixture", delivery);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());

        GooFlightPayload.STREAM_CODEC.encode(buf, sent);

        assertEquals(sent, GooFlightPayload.STREAM_CODEC.decode(buf));
    }
}
