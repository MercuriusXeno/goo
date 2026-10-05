package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.type.GooTypes;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The transformation broadcast carries the blob's goo type, both points,
 * the entity it becomes and its length whole
 * (decision model-transformation-is-one-animation).
 */
class TransformationPayloadTest {

    @Test
    void aTransformationRoundTripsThroughTheStreamCodec() {
        TransformationPayload sent = new TransformationPayload(GooTypes.VITAL, new Vec3(1.5, 64, -3.25),
                new Vec3(2.75, 64, -2), 4242, 16);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());

        TransformationPayload.STREAM_CODEC.encode(buf, sent);

        assertEquals(sent, TransformationPayload.STREAM_CODEC.decode(buf));
    }
}
