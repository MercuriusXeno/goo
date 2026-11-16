package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

/**
 * Server-to-client payload: a Molten cell began melting the block below it,
 * which the client draws heating to red hot over the heat's ticks; a heat of
 * none cools it, the melt ended with the block as it was.
 * stone-goes-red-hot-before-molten-turns-it-to-lava
 *
 * @param pos   the block heating
 * @param ticks the ticks it heats before it becomes lava, 0 where the melt ended
 */
public record RedHotPayload(BlockPos pos, int ticks) implements CustomPacketPayload {

    /** Payload type ID for registration. */
    public static final Type<RedHotPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Goo.MODID, "red_hot"));

    /** Stream codec for encoding/decoding. */
    public static final StreamCodec<FriendlyByteBuf, RedHotPayload> STREAM_CODEC =
            StreamCodec.of(RedHotPayload::encode, RedHotPayload::decode);

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(FriendlyByteBuf buf, RedHotPayload payload) {
        buf.writeBlockPos(payload.pos);
        buf.writeVarInt(payload.ticks);
    }

    private static RedHotPayload decode(FriendlyByteBuf buf) {
        return new RedHotPayload(buf.readBlockPos(), buf.readVarInt());
    }
}
