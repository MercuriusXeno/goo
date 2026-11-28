package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.program.ChannelAim;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;
import java.util.ArrayList;
import java.util.List;

/**
 * Server-to-client payload: the Mortar a Level hold carries over its disc,
 * sent each tick it rides to the players watching the disc's chunk, so each
 * draws the nine piles where no block stands; a carry of nine empty piles
 * clears the drawing.
 * carried-mortar-stops-being-a-block-on-the-level-plane
 *
 * @param holderId the channeling player's entity id
 * @param plane    the face the hold began on
 * @param middle   the block the cursor rests on, the disc's middle
 * @param piles    the nine piles' layers
 */
public record MortarCarryPayload(int holderId, ChannelAim.FacePlane plane, BlockPos middle, List<Integer> piles)
        implements CustomPacketPayload {

    /** Payload type ID for registration. */
    public static final Type<MortarCarryPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Goo.MODID, "mortar_carry"));

    /** Stream codec for encoding/decoding. */
    public static final StreamCodec<FriendlyByteBuf, MortarCarryPayload> STREAM_CODEC =
            StreamCodec.of(MortarCarryPayload::encode, MortarCarryPayload::decode);

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(FriendlyByteBuf buf, MortarCarryPayload payload) {
        buf.writeVarInt(payload.holderId);
        buf.writeBlockPos(payload.plane.block());
        buf.writeVarInt(payload.plane.face().get3DDataValue());
        buf.writeBlockPos(payload.middle);
        buf.writeVarInt(payload.piles.size());
        payload.piles.forEach(buf::writeVarInt);
    }

    private static MortarCarryPayload decode(FriendlyByteBuf buf) {
        int holderId = buf.readVarInt();
        ChannelAim.FacePlane plane = new ChannelAim.FacePlane(buf.readBlockPos(), Direction.from3DDataValue(buf.readVarInt()));
        BlockPos middle = buf.readBlockPos();
        int count = buf.readVarInt();
        List<Integer> piles = new ArrayList<>(count);
        for (int pile = 0; pile < count; pile++) {
            piles.add(buf.readVarInt());
        }
        return new MortarCarryPayload(holderId, plane, middle, List.copyOf(piles));
    }
}
