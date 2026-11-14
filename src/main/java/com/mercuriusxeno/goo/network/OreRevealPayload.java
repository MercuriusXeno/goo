package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.crystal.OreVeins;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;
import java.util.ArrayList;
import java.util.List;

/**
 * Server-to-caster payload: the ore veins a held sweep's front, Glimmer's
 * or Magnet's, first reached on one tick, each shown through walls from now
 * for its life.
 * decision held-sweeps-share-one-fast-shell-visual
 * decision glitter-sphere-icons-gem-ore-groups
 *
 * @param veins the veins first reached on the tick
 * @param life  the ticks each vein shows through walls once revealed
 */
public record OreRevealPayload(List<OreVeins.Vein> veins, int life) implements CustomPacketPayload {

    /** Payload type ID for registration. */
    public static final Type<OreRevealPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Goo.MODID, "ore_reveal"));

    /** Stream codec for encoding/decoding. */
    public static final StreamCodec<FriendlyByteBuf, OreRevealPayload> STREAM_CODEC =
            StreamCodec.of(OreRevealPayload::encode, OreRevealPayload::decode);

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(FriendlyByteBuf buf, OreRevealPayload payload) {
        buf.writeVarInt(payload.veins.size());
        for (OreVeins.Vein vein : payload.veins) {
            buf.writeIdentifier(vein.ore());
            buf.writeDouble(vein.centroid().x);
            buf.writeDouble(vein.centroid().y);
            buf.writeDouble(vein.centroid().z);
            buf.writeVarInt(vein.count());
            vein.blocks().forEach(block -> buf.writeLong(block.asLong()));
        }
        buf.writeVarInt(payload.life);
    }

    private static OreRevealPayload decode(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        List<OreVeins.Vein> veins = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            Identifier ore = buf.readIdentifier();
            Vec3 centroid = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
            int count = buf.readVarInt();
            List<BlockPos> blocks = new ArrayList<>(count);
            for (int block = 0; block < count; block++) {
                blocks.add(BlockPos.of(buf.readLong()));
            }
            veins.add(new OreVeins.Vein(ore, centroid, blocks));
        }
        return new OreRevealPayload(veins, buf.readVarInt());
    }
}
