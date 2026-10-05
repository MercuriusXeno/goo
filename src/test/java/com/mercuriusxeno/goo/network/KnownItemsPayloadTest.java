package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.data.KnownItems;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The known-items payloads carry the whole set on login and one learned item
 * after, each whole through its stream codec
 * (decision knowledge-capability-remembers-destroyed-items).
 */
class KnownItemsPayloadTest {

    private static final Identifier COBBLESTONE = Identifier.withDefaultNamespace("cobblestone");

    @Test
    void fullSetRoundTripsThroughTheStreamCodec() {
        KnownItemsSyncPayload sent = new KnownItemsSyncPayload(
                new KnownItems(Set.of(COBBLESTONE, Identifier.withDefaultNamespace("dirt"))));
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());

        KnownItemsSyncPayload.STREAM_CODEC.encode(buf, sent);

        assertEquals(sent, KnownItemsSyncPayload.STREAM_CODEC.decode(buf));
    }

    @Test
    void learnedItemRoundTripsThroughTheStreamCodec() {
        KnownItemLearnedPayload sent = new KnownItemLearnedPayload(COBBLESTONE);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());

        KnownItemLearnedPayload.STREAM_CODEC.encode(buf, sent);

        assertEquals(sent, KnownItemLearnedPayload.STREAM_CODEC.decode(buf));
    }
}
