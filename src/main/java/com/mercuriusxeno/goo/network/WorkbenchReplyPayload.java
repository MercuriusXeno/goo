package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Server-to-client payload: one part of the reply to a values manager request
 * (decision goo-workbench-opens-the-in-game-manager-screen).
 *
 * @param requestId the id of the request it answers
 * @param status    the reply's status, 200 for an answer
 * @param part      this part's index, from 0
 * @param parts     how many parts the text holds
 * @param text      this part's text
 */
public record WorkbenchReplyPayload(int requestId, int status, int part, int parts, String text)
        implements CustomPacketPayload {

    /** Payload type ID for registration. */
    public static final Type<WorkbenchReplyPayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(Goo.MODID, "workbench_reply"));

    /** Stream codec for encoding/decoding. */
    public static final StreamCodec<FriendlyByteBuf, WorkbenchReplyPayload> STREAM_CODEC =
        StreamCodec.of(WorkbenchReplyPayload::encode, WorkbenchReplyPayload::decode);

    /**
     * @param requestId the id of the request it answers
     * @param status    the reply's status
     * @param text      the whole reply text
     * @return the payloads carrying the text in parts
     */
    public static List<WorkbenchReplyPayload> parts(int requestId, int status, String text) {
        List<String> parts = WorkbenchPayloadParts.split(text);
        return IntStream.range(0, parts.size())
                .mapToObj(index -> new WorkbenchReplyPayload(requestId, status, index, parts.size(), parts.get(index)))
                .toList();
    }

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(FriendlyByteBuf buf, WorkbenchReplyPayload payload) {
        buf.writeVarInt(payload.requestId);
        buf.writeVarInt(payload.status);
        buf.writeVarInt(payload.part);
        buf.writeVarInt(payload.parts);
        buf.writeUtf(payload.text);
    }

    private static WorkbenchReplyPayload decode(FriendlyByteBuf buf) {
        int requestId = buf.readVarInt();
        int status = buf.readVarInt();
        int part = buf.readVarInt();
        int parts = buf.readVarInt();
        String text = buf.readUtf();
        return new WorkbenchReplyPayload(requestId, status, part, parts, text);
    }
}
