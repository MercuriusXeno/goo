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
 * Server-to-caster payload: one Glitter ping, the sphere the caster's
 * client draws as its sparkle shell and the gem ore veins it found, each
 * revealed the tick the sphere's front reaches it and shown for the ping's
 * life (decision glitter-sphere-icons-gem-ore-groups).
 *
 * @param origin the ping's center, where the caster stood
 * @param growth blocks the front grows each tick
 * @param radius the blocks the front reaches
 * @param veins  the veins found
 * @param reveal for each vein, the ticks after the ping began that the front reaches it
 * @param life   the ticks each vein shows through walls once revealed
 */
public record OreRevealPayload(Vec3 origin, double growth, int radius, List<OreVeins.Vein> veins,
                               List<Integer> reveal, int life)
        implements CustomPacketPayload {

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
        buf.writeDouble(payload.origin.x);
        buf.writeDouble(payload.origin.y);
        buf.writeDouble(payload.origin.z);
        buf.writeDouble(payload.growth);
        buf.writeVarInt(payload.radius);
        buf.writeVarInt(payload.veins.size());
        for (int index = 0; index < payload.veins.size(); index++) {
            OreVeins.Vein vein = payload.veins.get(index);
            buf.writeIdentifier(vein.ore());
            buf.writeDouble(vein.centroid().x);
            buf.writeDouble(vein.centroid().y);
            buf.writeDouble(vein.centroid().z);
            buf.writeVarInt(vein.count());
            vein.blocks().forEach(block -> buf.writeLong(block.asLong()));
            buf.writeVarInt(payload.reveal.get(index));
        }
        buf.writeVarInt(payload.life);
    }

    private static OreRevealPayload decode(FriendlyByteBuf buf) {
        Vec3 origin = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        double growth = buf.readDouble();
        int radius = buf.readVarInt();
        int size = buf.readVarInt();
        List<OreVeins.Vein> veins = new ArrayList<>(size);
        List<Integer> reveal = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            Identifier ore = buf.readIdentifier();
            Vec3 centroid = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
            int count = buf.readVarInt();
            List<BlockPos> blocks = new ArrayList<>(count);
            for (int block = 0; block < count; block++) {
                blocks.add(BlockPos.of(buf.readLong()));
            }
            veins.add(new OreVeins.Vein(ore, centroid, blocks));
            reveal.add(buf.readVarInt());
        }
        return new OreRevealPayload(origin, growth, radius, veins, reveal, buf.readVarInt());
    }
}
