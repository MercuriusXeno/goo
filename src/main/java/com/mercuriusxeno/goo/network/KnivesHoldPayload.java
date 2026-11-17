package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

/**
 * Client-to-server payload: a Knives hold begins as right click goes down,
 * or ends as it comes up. The server owns the hold's stage clock between the
 * two, so the knives it forms are the server's count.
 * decision knives-form-on-right-click-and-throw-on-left-click
 * decision knives-form-in-fibonacci-stages-to-twelve
 *
 * @param holding   true as the hold begins, false as it ends
 * @param gooTypeId the goo type string identifier
 * @param abilityId the held ability id string
 */
public record KnivesHoldPayload(boolean holding, String gooTypeId, String abilityId)
        implements CustomPacketPayload {

    /** Payload type ID for registration. */
    public static final Type<KnivesHoldPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Goo.MODID, "knives_hold"));

    /** Stream codec for encoding/decoding. */
    public static final StreamCodec<ByteBuf, KnivesHoldPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, KnivesHoldPayload::holding,
            ByteBufCodecs.STRING_UTF8, KnivesHoldPayload::gooTypeId,
            ByteBufCodecs.STRING_UTF8, KnivesHoldPayload::abilityId,
            KnivesHoldPayload::new);

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
