package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.BuildFootprint;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

/**
 * Client-to-server payload: the second right click of a Build, submitting
 * the footprint its drag sized and the rise its pitch set
 * (decision spire-rips-walls-and-platforms).
 *
 * @param gooTypeId the goo type string identifier
 * @param abilityId the selected ability id string
 * @param corner    the ground cell the press pinned
 * @param opposite  the ground cell across the footprint
 * @param rise      the rise the pitch set, in blocks
 * @param face      the face the press pinned, whose normal the rise runs along (decision build-goes-sideways)
 */
public record GooBuildPayload(String gooTypeId, String abilityId, BlockPos corner, BlockPos opposite,
                              int rise, Direction face) implements CustomPacketPayload {

    /** Payload type ID for registration. */
    public static final Type<GooBuildPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Goo.MODID, "goo_build"));

    /** Stream codec for encoding/decoding. */
    public static final StreamCodec<FriendlyByteBuf, GooBuildPayload> STREAM_CODEC =
            StreamCodec.of(GooBuildPayload::encode, GooBuildPayload::decode);

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * @return the footprint as submitted, before the server checks its caps
     */
    public BuildFootprint footprint() {
        return new BuildFootprint(corner, opposite, rise, face);
    }

    private static void encode(FriendlyByteBuf buf, GooBuildPayload payload) {
        buf.writeUtf(payload.gooTypeId);
        buf.writeUtf(payload.abilityId);
        buf.writeBlockPos(payload.corner);
        buf.writeBlockPos(payload.opposite);
        buf.writeVarInt(payload.rise);
        buf.writeVarInt(payload.face.ordinal());
    }

    private static GooBuildPayload decode(FriendlyByteBuf buf) {
        return new GooBuildPayload(buf.readUtf(), buf.readUtf(), buf.readBlockPos(), buf.readBlockPos(),
                buf.readVarInt(), Direction.values()[buf.readVarInt()]);
    }
}
