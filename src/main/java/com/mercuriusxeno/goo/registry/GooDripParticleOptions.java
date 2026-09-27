package com.mercuriusxeno.goo.registry;

import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.GooTypes;
import com.mojang.serialization.MapCodec;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;

/**
 * A drip particle naming the goo type it drips, so the client draws that
 * type's fluid sprite rather than a flat tint (decision particles-render-muted-goo-texture).
 *
 * @param type    the particle type
 * @param gooType the goo type the drip carries
 */
public record GooDripParticleOptions(ParticleType<GooDripParticleOptions> type,
                                     ResourceKey<GooTypeDefinition> gooType) implements ParticleOptions {

    /** The codec field naming the goo type. */
    private static final String GOO_TYPE_FIELD = "goo_type";

    /**
     * @param type the particle type the codec builds options for
     * @return the codec reading and writing the goo type
     */
    public static MapCodec<GooDripParticleOptions> codec(ParticleType<GooDripParticleOptions> type) {
        return GooTypes.KEY_CODEC.xmap(gooType -> new GooDripParticleOptions(type, gooType),
                GooDripParticleOptions::gooType).fieldOf(GOO_TYPE_FIELD);
    }

    /**
     * @param type the particle type the codec builds options for
     * @return the network codec carrying the goo type
     */
    public static StreamCodec<ByteBuf, GooDripParticleOptions> streamCodec(ParticleType<GooDripParticleOptions> type) {
        return ResourceKey.streamCodec(GooTypes.REGISTRY).map(gooType -> new GooDripParticleOptions(type, gooType),
                GooDripParticleOptions::gooType);
    }

    @Override
    public ParticleType<GooDripParticleOptions> getType() {
        return type;
    }
}
