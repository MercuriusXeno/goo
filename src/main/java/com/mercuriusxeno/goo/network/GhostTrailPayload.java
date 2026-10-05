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
 * Server-to-client payload: lay a trail of ghosts of an entity along the
 * segment it just jumped, in a goo type's color, fading over a life.
 * Decision ghost-trail-spans-the-blink.
 *
 * @param entityId    the entity whose model and pose the ghosts take
 * @param source      the world point it left
 * @param destination the world point it landed
 * @param gooType     the goo type whose color the ghosts wear
 * @param lifeTicks   the game ticks the trail takes to fade out
 */
public record GhostTrailPayload(int entityId, Vec3 source, Vec3 destination, ResourceKey<GooTypeDefinition> gooType,
        int lifeTicks) implements CustomPacketPayload {

    /** Payload type ID for registration. */
    public static final Type<GhostTrailPayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(Goo.MODID, "ghost_trail"));

    /** Stream codec for encoding/decoding. */
    public static final StreamCodec<FriendlyByteBuf, GhostTrailPayload> STREAM_CODEC =
        StreamCodec.of(GhostTrailPayload::encode, GhostTrailPayload::decode);

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(FriendlyByteBuf buf, GhostTrailPayload payload) {
        buf.writeVarInt(payload.entityId);
        Vec3.STREAM_CODEC.encode(buf, payload.source);
        Vec3.STREAM_CODEC.encode(buf, payload.destination);
        GooTypes.KEY_STREAM_CODEC.encode(buf, payload.gooType);
        buf.writeVarInt(payload.lifeTicks);
    }

    private static GhostTrailPayload decode(FriendlyByteBuf buf) {
        return new GhostTrailPayload(buf.readVarInt(), Vec3.STREAM_CODEC.decode(buf), Vec3.STREAM_CODEC.decode(buf),
                GooTypes.KEY_STREAM_CODEC.decode(buf), buf.readVarInt());
    }
}
