package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.program.SoundCue;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

/**
 * Server-to-client payload: one sound whose source sits at the receiving
 * player's own head, which their client plays at the listener, unpanned and
 * unattenuated, while every other player hears it positioned.
 * goo-sound-at-the-listener-reaches-both-ears
 *
 * @param cue the sound
 */
public record ListenerSoundPayload(SoundCue cue) implements CustomPacketPayload {

    /** Payload type ID for registration. */
    public static final Type<ListenerSoundPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Goo.MODID, "listener_sound"));

    /** Stream codec for encoding/decoding. */
    public static final StreamCodec<ByteBuf, ListenerSoundPayload> STREAM_CODEC = StreamCodec.composite(
            SoundCue.STREAM_CODEC, ListenerSoundPayload::cue,
            ListenerSoundPayload::new);

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
