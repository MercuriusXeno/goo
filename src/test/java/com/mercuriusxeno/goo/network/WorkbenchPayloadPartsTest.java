package com.mercuriusxeno.goo.network;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that a workbench text past the custom payload caps travels in parts through each payload's codec and joins
 * back whole, serverbound and clientbound (decision goo-workbench-opens-the-in-game-manager-screen).
 */
class WorkbenchPayloadPartsTest {
    // ServerboundCustomPayloadPacket.MAX_PAYLOAD_SIZE and its clientbound twin, read from the 26.1 sources.
    private static final int SERVERBOUND_CAP = 32767;
    private static final int CLIENTBOUND_CAP = 1048576;

    /**
     * A text of multi-byte characters well past the clientbound cap, with a surrogate pair on every part's edge.
     */
    private static String pastTheCaps() {
        StringBuilder text = new StringBuilder();
        while (text.length() < 3 * CLIENTBOUND_CAP / 2) {
            text.append("{\"metal\": \"4 $iron.metal\", \"name\": \"é€😀\"},");
        }
        return text.toString();
    }

    private static <T> T roundTrip(T payload, net.minecraft.network.codec.StreamCodec<FriendlyByteBuf, T> codec,
                                   int cap) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        codec.encode(buffer, payload);
        assertTrue(buffer.readableBytes() <= cap, buffer.readableBytes() + " bytes pass the cap of " + cap);
        return codec.decode(buffer);
    }

    /**
     * Each serverbound part encodes under 32767 bytes, and the decoded parts join back to the text.
     */
    @Test
    void serverboundTextPastTheCapRoundTripsInParts() {
        String text = pastTheCaps();
        List<WorkbenchRequestPayload> parts = WorkbenchRequestPayload.parts(7, "model/save", text);
        assertTrue(parts.size() > 1);
        WorkbenchPayloadParts joiner = new WorkbenchPayloadParts();
        Optional<String> joined = Optional.empty();
        for (WorkbenchRequestPayload part : parts.reversed()) {
            WorkbenchRequestPayload decoded = roundTrip(part, WorkbenchRequestPayload.STREAM_CODEC, SERVERBOUND_CAP);
            joined = joiner.accept("player#" + decoded.requestId(), decoded.part(), decoded.parts(), decoded.text());
        }
        assertEquals(Optional.of(text), joined);
    }

    /**
     * Each clientbound part encodes under 1048576 bytes, and the decoded parts join back to the text.
     */
    @Test
    void clientboundTextPastTheCapRoundTripsInParts() {
        String text = pastTheCaps();
        List<WorkbenchReplyPayload> parts = WorkbenchReplyPayload.parts(9, 200, text);
        WorkbenchPayloadParts joiner = new WorkbenchPayloadParts();
        Optional<String> joined = Optional.empty();
        for (WorkbenchReplyPayload part : parts) {
            WorkbenchReplyPayload decoded = roundTrip(part, WorkbenchReplyPayload.STREAM_CODEC, CLIENTBOUND_CAP);
            joined = joiner.accept(Integer.toString(decoded.requestId()), decoded.part(), decoded.parts(),
                    decoded.text());
        }
        assertEquals(Optional.of(text), joined);
    }

    /**
     * An empty text travels as one empty part.
     */
    @Test
    void emptyTextIsOneEmptyPart() {
        assertEquals(List.of(""), WorkbenchPayloadParts.split(""));
    }
}
