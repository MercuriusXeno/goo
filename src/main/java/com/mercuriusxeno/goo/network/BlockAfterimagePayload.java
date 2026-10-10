package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.NonNull;

/**
 * Server-to-client payload: leave an afterimage of a block as it stood, its
 * shape rippling out of its cell in silhouettes and fading in a goo type's
 * color, the shared afterimage a reaped plant leaves. Sent to the players
 * watching the block's chunk.
 * reap-breeze-harvests-and-replants
 *
 * @param pos       the block's cell
 * @param stateId   the block as it stood, by its state id, whose shape the echo takes
 * @param gooType   the goo type whose color the echo wears
 * @param lifeTicks the game ticks the echo takes to fade out
 */
public record BlockAfterimagePayload(BlockPos pos, int stateId, ResourceKey<GooTypeDefinition> gooType, int lifeTicks)
        implements CustomPacketPayload {

    /** Payload type ID for registration. */
    public static final Type<BlockAfterimagePayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(Goo.MODID, "block_afterimage"));

    /** Stream codec for encoding/decoding. */
    public static final StreamCodec<FriendlyByteBuf, BlockAfterimagePayload> STREAM_CODEC =
        StreamCodec.of(BlockAfterimagePayload::encode, BlockAfterimagePayload::decode);

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(FriendlyByteBuf buf, BlockAfterimagePayload payload) {
        buf.writeBlockPos(payload.pos);
        buf.writeVarInt(payload.stateId);
        GooTypes.KEY_STREAM_CODEC.encode(buf, payload.gooType);
        buf.writeVarInt(payload.lifeTicks);
    }

    private static BlockAfterimagePayload decode(FriendlyByteBuf buf) {
        return new BlockAfterimagePayload(buf.readBlockPos(), buf.readVarInt(), GooTypes.KEY_STREAM_CODEC.decode(buf),
                buf.readVarInt());
    }
}
