package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.zone.ConjuredDoor;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

/**
 * Server-to-client payload: the door a player channeling Zone has
 * conjured this tick, sent to the caster and every player tracking them, so
 * each draws it growing, then moving into the caster; a door no longer sent
 * collapses on its own.
 * decision channeling-to-the-pocket-conjures-a-door-that-envelops-the-player
 *
 * @param casterId the caster's entity id
 * @param door     the door
 */
public record ConjuredDoorPayload(int casterId, ConjuredDoor door) implements CustomPacketPayload {

    /** Payload type ID for registration. */
    public static final Type<ConjuredDoorPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Goo.MODID, "conjured_door"));

    /** Stream codec for encoding/decoding. */
    public static final StreamCodec<ByteBuf, ConjuredDoorPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ConjuredDoorPayload::casterId,
            ConjuredDoor.STREAM_CODEC, ConjuredDoorPayload::door,
            ConjuredDoorPayload::new);

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
