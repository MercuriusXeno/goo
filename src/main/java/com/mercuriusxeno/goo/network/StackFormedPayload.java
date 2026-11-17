package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

/**
 * Server-to-client payload: what a caster's stacking hold has formed, Knives'
 * knives or Dart's javelins, sent to the caster and the players watching each
 * time the count changes, 0 once the hold ends, so every client draws the
 * server's stack.
 * decision knives-emerge-from-the-blob-and-hover-in-a-fan
 * decision knives-charge-one-per-three-ticks-up-to-ten
 * decision dart-stacks-three-javelins-one-per-ten-ticks
 *
 * @param casterId the caster's entity id
 * @param formed   the count formed, 0 for no hold
 * @param javelins whether the hold stacks Dart's javelins rather than Knives' knives
 */
public record StackFormedPayload(int casterId, int formed, boolean javelins) implements CustomPacketPayload {

    /** Payload type ID for registration. */
    public static final Type<StackFormedPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Goo.MODID, "stack_formed"));

    /** Stream codec for encoding/decoding. */
    public static final StreamCodec<ByteBuf, StackFormedPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, StackFormedPayload::casterId,
            ByteBufCodecs.VAR_INT, StackFormedPayload::formed,
            ByteBufCodecs.BOOL, StackFormedPayload::javelins,
            StackFormedPayload::new);

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
