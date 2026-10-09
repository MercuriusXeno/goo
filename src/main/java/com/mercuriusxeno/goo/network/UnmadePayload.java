package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;
import java.util.Map;

/**
 * Server-to-client payload: a block or mob has been unmade, so the client
 * morphs its remains, a blob of the goo it melted into, into the goo item's
 * sprite where the item will drop once the morph ends.
 * decision unmake-waves-dissolve-by-crucible-cost
 *
 * @param at    where the remains stand on the ground and the item drops
 * @param goo   the goo the remains are, each type's amount
 * @param size  how big the remains start, in blocks
 */
public record UnmadePayload(Vec3 at, Map<ResourceKey<GooTypeDefinition>, Integer> goo, float size)
        implements CustomPacketPayload {

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
        GooAmounts.write(buf, payload.goo);
        buf.writeFloat(payload.size);
    }

    private static UnmadePayload decode(FriendlyByteBuf buf) {
        Vec3 at = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        return new UnmadePayload(at, GooAmounts.read(buf), buf.readFloat());
    }
}
