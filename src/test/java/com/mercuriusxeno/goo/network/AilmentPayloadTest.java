package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.program.AilmentKind;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The ailment broadcast carries the entity, the ailment and its duration
 * whole (decision ailment-overlay-shader-per-ailment).
 */
class AilmentPayloadTest {

    @ParameterizedTest
    @EnumSource(AilmentKind.class)
    void everyAilmentRoundTripsThroughTheStreamCodec(AilmentKind kind) {
        AilmentPayload sent = new AilmentPayload(4321, kind, 1200);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());

        AilmentPayload.STREAM_CODEC.encode(buf, sent);

        assertEquals(sent, AilmentPayload.STREAM_CODEC.decode(buf));
    }
}
