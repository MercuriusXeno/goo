package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.program.SoundCue;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

/**
 * Server-to-client payload: Spikes goo struck a thing walking on it, sent to
 * the players tracking the thing so every client draws spikes rising out of
 * the cell under it and retracting, sounding the retract shink as they go.
 * urchin-rests-bristled-and-spears
 * urchin-spikes-shink-out-and-shink-back
 *
 * @param cell         the Spikes cell the spikes rise out of
 * @param struckId     the id of the thing they rise under
 * @param retractShink the shink the spikes sound as they retract
 * @param retractFrame the ticks after the strike the spikes start retracting
 */
public record SpikesRisePayload(BlockPos cell, int struckId, SoundCue retractShink, int retractFrame)
        implements CustomPacketPayload {

    /** Payload type ID for registration. */
    public static final Type<SpikesRisePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Goo.MODID, "spikes_rise"));

    /** Stream codec for encoding/decoding. */
    public static final StreamCodec<FriendlyByteBuf, SpikesRisePayload> STREAM_CODEC =
            StreamCodec.of(SpikesRisePayload::encode, SpikesRisePayload::decode);

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(FriendlyByteBuf buf, SpikesRisePayload payload) {
        buf.writeBlockPos(payload.cell);
        buf.writeVarInt(payload.struckId);
        SoundCue.STREAM_CODEC.encode(buf, payload.retractShink);
        buf.writeVarInt(payload.retractFrame);
    }

    private static SpikesRisePayload decode(FriendlyByteBuf buf) {
        return new SpikesRisePayload(buf.readBlockPos(), buf.readVarInt(), SoundCue.STREAM_CODEC.decode(buf),
                buf.readVarInt());
    }
}
