package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.quantum.Gate;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;
import java.util.List;

/**
 * Server-to-client payload: the Quantum holes standing open in the player's
 * level, each as its two mouths, so the client draws the view through each
 * the way it draws a portal's. Sent whenever a hole opens or closes.
 * hole-closes-the-space-to-an-opening-ahead
 *
 * @param holes each open hole's mouths
 */
public record HolesSyncPayload(List<Pair> holes) implements CustomPacketPayload {

    /** Payload type ID for registration. */
    public static final Type<HolesSyncPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Goo.MODID, "holes_sync"));

    /** Stream codec for encoding/decoding. */
    public static final StreamCodec<ByteBuf, HolesSyncPayload> STREAM_CODEC = StreamCodec.composite(
            Pair.STREAM_CODEC.apply(ByteBufCodecs.list()), HolesSyncPayload::holes,
            HolesSyncPayload::new);

    /**
     * A hole's two mouths.
     *
     * @param entry the mouth on the struck face
     * @param exit  the mouth on the wall's far face
     */
    public record Pair(Gate entry, Gate exit) {

        /** Sends a pair. */
        public static final StreamCodec<ByteBuf, Pair> STREAM_CODEC = StreamCodec.composite(
                Gate.STREAM_CODEC, Pair::entry,
                Gate.STREAM_CODEC, Pair::exit,
                Pair::new);
    }

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
