package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;
import java.util.List;

/**
 * Server-to-client payload: one tick of a held stream healed these living
 * things, so the client homes vital goo from the glove to each and plays
 * pink healing stars on it. Sent to the players watching the caster and to
 * the caster itself.
 * vitality-waves-regenerate-and-court
 *
 * @param casterId  the streaming player
 * @param glove     the glove hand the stream left from
 * @param healedIds the living things the tick healed, the caster among them when it healed
 */
public record StreamHealedPayload(int casterId, Vec3 glove, List<Integer> healedIds) implements CustomPacketPayload {

    /** Payload type ID for registration. */
    public static final Type<StreamHealedPayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(Goo.MODID, "stream_healed"));

    /** Stream codec for encoding/decoding. */
    public static final StreamCodec<FriendlyByteBuf, StreamHealedPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, StreamHealedPayload::casterId,
            Vec3.STREAM_CODEC, StreamHealedPayload::glove,
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), StreamHealedPayload::healedIds,
            StreamHealedPayload::new);

    /**
     * Copies the healed list so the record holds it unmodifiable.
     */
    public StreamHealedPayload {
        healedIds = List.copyOf(healedIds);
    }

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
