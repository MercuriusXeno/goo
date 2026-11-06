package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;

/**
 * Server-to-caster payload: one tick of a held sweep, Scry's, Glitter's or
 * Magnet's, sent to the caster alone, whose client draws the pulse's sphere
 * from its origin and the thin shell of air-exposed faces at its rim, in the
 * colors of the goo type it names.
 * decision held-sweeps-share-one-fast-shell-visual
 * decision scry-sphere-reveals-faces-and-glistens-mobs
 *
 * @param origin   where the pulse began
 * @param radius   the pulse's radius in blocks
 * @param range    the radius the pulse reaches
 * @param strength how strongly the pulse shows, fading over its rest
 * @param gooType  the goo type whose colors tint the sphere and the shell
 */
public record HeldSweepPayload(Vec3 origin, float radius, float range, float strength,
                               ResourceKey<GooTypeDefinition> gooType) implements CustomPacketPayload {

    /** Payload type ID for registration. */
    public static final Type<HeldSweepPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Goo.MODID, "held_sweep"));

    /** Stream codec for encoding/decoding. */
    public static final StreamCodec<FriendlyByteBuf, HeldSweepPayload> STREAM_CODEC =
            StreamCodec.of(HeldSweepPayload::encode, HeldSweepPayload::decode);

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(FriendlyByteBuf buf, HeldSweepPayload payload) {
        buf.writeDouble(payload.origin.x);
        buf.writeDouble(payload.origin.y);
        buf.writeDouble(payload.origin.z);
        buf.writeFloat(payload.radius);
        buf.writeFloat(payload.range);
        buf.writeFloat(payload.strength);
        GooTypes.KEY_STREAM_CODEC.encode(buf, payload.gooType);
    }

    private static HeldSweepPayload decode(FriendlyByteBuf buf) {
        Vec3 origin = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        return new HeldSweepPayload(origin, buf.readFloat(), buf.readFloat(), buf.readFloat(),
                GooTypes.KEY_STREAM_CODEC.decode(buf));
    }
}
