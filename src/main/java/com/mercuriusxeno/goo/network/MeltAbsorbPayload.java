package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.NonNull;

/**
 * Server-to-client payload: Melt's blob landed on a stone face and sinks
 * into it as the stone mingles into lava.
 * melt-blob-absorbs-into-lava
 *
 * @param gooType the goo type of the blob
 * @param struck  the block the blob sinks into
 * @param face    the face it landed on
 */
public record MeltAbsorbPayload(ResourceKey<GooTypeDefinition> gooType, BlockPos struck, Direction face)
        implements CustomPacketPayload {

    /** Payload type ID for registration. */
    public static final Type<MeltAbsorbPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Goo.MODID, "melt_absorb"));

    /** Stream codec for encoding/decoding. */
    public static final StreamCodec<FriendlyByteBuf, MeltAbsorbPayload> STREAM_CODEC =
            StreamCodec.of(MeltAbsorbPayload::encode, MeltAbsorbPayload::decode);

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(FriendlyByteBuf buf, MeltAbsorbPayload payload) {
        GooTypes.KEY_STREAM_CODEC.encode(buf, payload.gooType);
        buf.writeBlockPos(payload.struck);
        buf.writeVarInt(payload.face.get3DDataValue());
    }

    private static MeltAbsorbPayload decode(FriendlyByteBuf buf) {
        return new MeltAbsorbPayload(GooTypes.KEY_STREAM_CODEC.decode(buf), buf.readBlockPos(),
                Direction.from3DDataValue(buf.readVarInt()));
    }
}
