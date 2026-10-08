package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;

/**
 * Server-to-client payload: a block or mob has been unmade, so the client
 * morphs its blobby remains into the goo item's sprite where the item will
 * drop once the morph ends.
 * decision unmake-waves-dissolve-by-crucible-cost
 *
 * @param at        where the remains stand and the item drops
 * @param gooTypeId the short id of the goo type whose item the remains morph into
 * @param size      how big the remains start, in blocks
 */
public record UnmadePayload(Vec3 at, String gooTypeId, float size) implements CustomPacketPayload {

    /** Payload type ID for registration. */
    public static final Type<UnmadePayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(Goo.MODID, "unmade"));

    /** Stream codec for encoding/decoding. */
    public static final StreamCodec<FriendlyByteBuf, UnmadePayload> STREAM_CODEC =
        StreamCodec.of(UnmadePayload::encode, UnmadePayload::decode);

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(FriendlyByteBuf buf, UnmadePayload payload) {
        buf.writeDouble(payload.at.x);
        buf.writeDouble(payload.at.y);
        buf.writeDouble(payload.at.z);
        buf.writeUtf(payload.gooTypeId);
        buf.writeFloat(payload.size);
    }

    private static UnmadePayload decode(FriendlyByteBuf buf) {
        Vec3 at = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        return new UnmadePayload(at, buf.readUtf(), buf.readFloat());
    }
}
