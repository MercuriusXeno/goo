package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.throwing.ConeShape;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;

/**
 * Server-to-client payload: one tick of a mister's spray, a fine mist over the cone in the goo's colours, sent to
 * the caster's watchers in place of drawn drips, since a mister throws none.
 * decision mister-draws-mist-and-lays-one-layer-on-bare-spots
 *
 * @param casterId  the spraying player
 * @param from      where the mist leaves, the sprayer's nozzle as the server heard it
 * @param axis      the cone's axis
 * @param coneShape the cone's widths across and up and down
 * @param particle  the goo's spray particle
 * @param gooType   the sprayed goo type, whose colours the mist takes
 * @param reach     how far the mist reaches, in blocks
 */
public record SprayMistPayload(int casterId, Vec3 from, Vec3 axis, ConeShape coneShape, Identifier particle,
                               ResourceKey<GooTypeDefinition> gooType, double reach) implements CustomPacketPayload {

    /** Payload type ID for registration. */
    public static final Type<SprayMistPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Goo.MODID, "spray_mist"));

    /** Stream codec for encoding/decoding. */
    public static final StreamCodec<FriendlyByteBuf, SprayMistPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SprayMistPayload::casterId,
            Vec3.STREAM_CODEC, SprayMistPayload::from,
            Vec3.STREAM_CODEC, SprayMistPayload::axis,
            ConeShape.STREAM_CODEC, SprayMistPayload::coneShape,
            Identifier.STREAM_CODEC, SprayMistPayload::particle,
            GooTypes.KEY_STREAM_CODEC, SprayMistPayload::gooType,
            ByteBufCodecs.DOUBLE, SprayMistPayload::reach,
            SprayMistPayload::new);

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
