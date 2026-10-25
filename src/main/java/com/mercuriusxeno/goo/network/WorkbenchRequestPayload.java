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
 * Client-to-server payload: one part of a values manager request, naming a workbench operation and carrying its
 * posted text (decision goo-workbench-opens-the-in-game-manager-screen).
 *
 * @param requestId the request's id, which its reply carries back
 * @param operation the workbench operation's name
 * @param part      this part's index, from 0
 * @param parts     how many parts the text holds
 * @param text      this part's text
 */
public record WorkbenchRequestPayload(int requestId, String operation, int part, int parts, String text)
        implements CustomPacketPayload {

    /** Payload type ID for registration. */
    public static final Type<WorkbenchRequestPayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(Goo.MODID, "workbench_request"));

    /** Stream codec for encoding/decoding. */
    public static final StreamCodec<FriendlyByteBuf, WorkbenchRequestPayload> STREAM_CODEC =
        StreamCodec.of(WorkbenchRequestPayload::encode, WorkbenchRequestPayload::decode);

    /**
     * @param requestId the request's id
     * @param operation the workbench operation's name
     * @param text      the whole posted text
     * @return the payloads carrying the text in parts
     */
    public static List<WorkbenchRequestPayload> parts(int requestId, String operation, String text) {
        List<String> parts = WorkbenchPayloadParts.split(text);
        return IntStream.range(0, parts.size())
                .mapToObj(index -> new WorkbenchRequestPayload(requestId, operation, index, parts.size(),
                        parts.get(index)))
                .toList();
    }

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(FriendlyByteBuf buf, WorkbenchRequestPayload payload) {
        buf.writeVarInt(payload.requestId);
        buf.writeUtf(payload.operation);
        buf.writeVarInt(payload.part);
        buf.writeVarInt(payload.parts);
        buf.writeUtf(payload.text);
    }

    private static WorkbenchRequestPayload decode(FriendlyByteBuf buf) {
        int requestId = buf.readVarInt();
        String operation = buf.readUtf();
        int part = buf.readVarInt();
        int parts = buf.readVarInt();
        String text = buf.readUtf();
        return new WorkbenchRequestPayload(requestId, operation, part, parts, text);
    }
}
