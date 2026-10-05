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
 * Server-to-client payload: leave an afterimage of an entity, a translucent
 * echo of its model frozen in its pose, at a point, fading over its life in
 * a goo type's color. Sent to the players tracking the entity and to the
 * entity itself.
 * Decision afterimage-is-one-shared-effect.
 *
 * @param entityId  the entity whose model and pose the echo takes
 * @param position  the world point the echo stands at
 * @param gooType   the goo type whose color the echo wears
 * @param lifeTicks the game ticks the echo takes to fade out
 */
public record AfterimagePayload(int entityId, Vec3 position, ResourceKey<GooTypeDefinition> gooType, int lifeTicks)
        implements CustomPacketPayload {

    /** Payload type ID for registration. */
    public static final Type<AfterimagePayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(Goo.MODID, "afterimage"));

    /** Stream codec for encoding/decoding. */
    public static final StreamCodec<FriendlyByteBuf, AfterimagePayload> STREAM_CODEC =
        StreamCodec.of(AfterimagePayload::encode, AfterimagePayload::decode);

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(FriendlyByteBuf buf, AfterimagePayload payload) {
        buf.writeVarInt(payload.entityId);
        Vec3.STREAM_CODEC.encode(buf, payload.position);
        GooTypes.KEY_STREAM_CODEC.encode(buf, payload.gooType);
        buf.writeVarInt(payload.lifeTicks);
    }

    private static AfterimagePayload decode(FriendlyByteBuf buf) {
        return new AfterimagePayload(buf.readVarInt(), Vec3.STREAM_CODEC.decode(buf),
                GooTypes.KEY_STREAM_CODEC.decode(buf), buf.readVarInt());
    }
}
