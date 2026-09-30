package com.mercuriusxeno.goo.registry;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Goo's own swirling ring particle, sent in front of a layer about to
 * break (decision goo-swirl-ring-particle): the face whose plane the disc
 * lies in, the ring's radius in blocks, and the goo type's theme color.
 *
 * @param face   the direction the disc turns to; the disc lies square to its axis
 * @param radius the disc's radius in blocks
 * @param color  the theme color as packed RGB
 */
public record GooRingParticleOptions(Direction face, float radius, int color) implements ParticleOptions {

    public static final MapCodec<GooRingParticleOptions> CODEC =
            RecordCodecBuilder.mapCodec(i -> i.group(
                    Direction.CODEC.fieldOf("face").forGetter(GooRingParticleOptions::face),
                    Codec.FLOAT.fieldOf("radius").forGetter(GooRingParticleOptions::radius),
                    Codec.INT.fieldOf("color").forGetter(GooRingParticleOptions::color)
            ).apply(i, GooRingParticleOptions::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, GooRingParticleOptions> STREAM_CODEC =
            StreamCodec.composite(
                    Direction.STREAM_CODEC, GooRingParticleOptions::face,
                    ByteBufCodecs.FLOAT, GooRingParticleOptions::radius,
                    ByteBufCodecs.INT, GooRingParticleOptions::color,
                    GooRingParticleOptions::new
            );

    @Override
    public ParticleType<GooRingParticleOptions> getType() {
        return GooParticles.GOO_RING.get();
    }
}
