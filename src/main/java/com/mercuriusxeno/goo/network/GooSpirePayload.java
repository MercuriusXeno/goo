package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.SpireFootprint;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

/**
 * Client-to-server payload: the second right click of a Spire, submitting
 * the footprint its drag sized and the rise its pitch set
 * (decision spire-rips-walls-and-platforms).
 *
 * @param gooTypeId the goo type string identifier
 * @param abilityId the selected ability id string
 * @param corner    the ground cell the press pinned
 * @param opposite  the ground cell across the footprint
 * @param rise      the rise the pitch set, in blocks
 */
public record GooSpirePayload(String gooTypeId, String abilityId, BlockPos corner, BlockPos opposite,
                              int rise) implements CustomPacketPayload {

    /** Payload type ID for registration. */
    public static final Type<GooSpirePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Goo.MODID, "goo_spire"));

    /** Stream codec for encoding/decoding. */
    public static final StreamCodec<FriendlyByteBuf, GooSpirePayload> STREAM_CODEC =
            StreamCodec.of(GooSpirePayload::encode, GooSpirePayload::decode);

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * @return the footprint as submitted, before the server checks its caps
     */
    public SpireFootprint footprint() {
        return new SpireFootprint(corner, opposite, rise);
    }

    private static void encode(FriendlyByteBuf buf, GooSpirePayload payload) {
        buf.writeUtf(payload.gooTypeId);
        buf.writeUtf(payload.abilityId);
        buf.writeBlockPos(payload.corner);
        buf.writeBlockPos(payload.opposite);
        buf.writeVarInt(payload.rise);
    }

    private static GooSpirePayload decode(FriendlyByteBuf buf) {
        return new GooSpirePayload(buf.readUtf(), buf.readUtf(), buf.readBlockPos(), buf.readBlockPos(),
                buf.readVarInt());
    }
}
