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
 * Server-to-caster payload: one live pulse of a held sweep, Scry's,
 * Glitter's or Magnet's, on one tick, sent to the caster alone, whose client
 * draws that pulse's front from its origin and lights the walls it hits, in
 * the colors of the goo type it names, and outlines the mobs it crosses
 * where the sweep paints mobs; a tick with two fronts out sends two.
 * decision held-sweeps-share-one-fast-shell-visual
 * decision scry-sphere-reveals-faces-and-glistens-mobs
 *
 * @param pulse      names the pulse, so the client keeps each pulse's walls apart
 * @param origin     where the pulse began
 * @param radius     the front's radius in blocks
 * @param range      the radius the front reaches
 * @param gooType    the goo type whose colors tint the front and the walls
 * @param paintsMobs whether the front outlines the mobs it crosses, as Scry's does
 */
public record HeldSweepPayload(int pulse, Vec3 origin, float radius, float range,
                               ResourceKey<GooTypeDefinition> gooType, boolean paintsMobs)
        implements CustomPacketPayload {

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
        buf.writeVarInt(payload.pulse);
        buf.writeDouble(payload.origin.x);
        buf.writeDouble(payload.origin.y);
        buf.writeDouble(payload.origin.z);
        buf.writeFloat(payload.radius);
        buf.writeFloat(payload.range);
        GooTypes.KEY_STREAM_CODEC.encode(buf, payload.gooType);
        buf.writeBoolean(payload.paintsMobs);
    }

    private static HeldSweepPayload decode(FriendlyByteBuf buf) {
        int pulse = buf.readVarInt();
        Vec3 origin = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        float radius = buf.readFloat();
        float range = buf.readFloat();
        ResourceKey<GooTypeDefinition> gooType = GooTypes.KEY_STREAM_CODEC.decode(buf);
        return new HeldSweepPayload(pulse, origin, radius, range, gooType, buf.readBoolean());
    }
}
