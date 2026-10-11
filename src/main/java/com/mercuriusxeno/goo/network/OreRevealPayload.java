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
 * Server-to-caster payload: one held tick of Glitter, the front the
 * caster's client draws as its sparkle shell and the gem ore veins the
 * front first reached on the tick, each shown through walls from now for
 * its life; the payloads stop when the hold ends, and the front with them
 * (decision glitter-sphere-icons-gem-ore-groups).
 *
 * @param origin where the hold began, the front's center
 * @param growth blocks the front grows each held tick
 * @param front  the front's radius after the tick
 * @param radius the radius the front reaches at most
 * @param veins  the veins first reached on the tick
 * @param life   the ticks each vein shows through walls once revealed
 */
public record OreRevealPayload(Vec3 origin, double growth, double front, int radius, List<OreVeins.Vein> veins,
                               int life)
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
        buf.writeDouble(payload.front);
        buf.writeVarInt(payload.radius);
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
        Vec3 origin = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        double growth = buf.readDouble();
        double front = buf.readDouble();
        int radius = buf.readVarInt();
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
        return new OreRevealPayload(origin, growth, front, radius, veins, buf.readVarInt());
    }
}
