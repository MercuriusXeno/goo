package com.mercuriusxeno.goo.registry;

import com.mercuriusxeno.goo.Goo;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Registers custom particle types for the goo mod.
 */
public class GooParticles {

    public static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES =
        DeferredRegister.create(Registries.PARTICLE_TYPE, Goo.MODID);

    /** Gravity-affected spark particle for crucible rod-contact effects. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> GOO_SPARK =
        PARTICLE_TYPES.register("goo_spark", () -> new SimpleParticleType(false));

    /**
     * The particle a chain marker's explosion names in place of vanilla's
     * explosion particles; its client provider spawns nothing, so the goo
     * type's own burnout explosion is the one seen (decision
     * elemental-explosion-per-type).
     */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> SILENT_BLAST =
        PARTICLE_TYPES.register("silent_blast", () -> new SimpleParticleType(false));

    /** Color-tinted bubble particle spawned during goo extraction. */
    public static final DeferredHolder<ParticleType<?>, ParticleType<ColorParticleOption>> GOO_BUBBLE =
        PARTICLE_TYPES.register("goo_bubble", GooParticles::colorParticleType);

    /** The trail-drip: 2x3 slime drip shed by thrown goo blobs mid-flight. */
    public static final DeferredHolder<ParticleType<?>, ParticleType<ColorParticleOption>> TRAIL_DRIP =
        PARTICLE_TYPES.register("trail_drip", GooParticles::colorParticleType);

    /** Brief splat when a trail-drip hits the ground. */
    public static final DeferredHolder<ParticleType<?>, ParticleType<ColorParticleOption>> TRAIL_DRIP_LAND =
        PARTICLE_TYPES.register("trail_drip_land", GooParticles::colorParticleType);

    /** The tap-drip: square drop falling straight down from a tap's spigot. */
    public static final DeferredHolder<ParticleType<?>, ParticleType<GooDripParticleOptions>> TAP_DRIP =
        PARTICLE_TYPES.register("tap_drip", GooParticles::gooDripParticleType);

    /** Square splat when a tap-drip hits the ground. */
    public static final DeferredHolder<ParticleType<?>, ParticleType<GooDripParticleOptions>> TAP_DRIP_LAND =
        PARTICLE_TYPES.register("tap_drip_land", GooParticles::gooDripParticleType);

    /** Goo's swirling ring, drawn in front of a layer about to break (decision goo-swirl-ring-particle). */
    public static final DeferredHolder<ParticleType<?>, ParticleType<GooRingParticleOptions>> GOO_RING =
        PARTICLE_TYPES.register("goo_ring", () -> new ParticleType<>(false) {
            @Override
            public MapCodec<GooRingParticleOptions> codec() {
                return GooRingParticleOptions.CODEC;
            }

            @Override
            public StreamCodec<? super RegistryFriendlyByteBuf, GooRingParticleOptions> streamCodec() {
                return GooRingParticleOptions.STREAM_CODEC;
            }
        });

    /** Radial gradient fog puff for blob flight trails. */
    public static final DeferredHolder<ParticleType<?>, ParticleType<ColorParticleOption>> GOO_FOG =
        PARTICLE_TYPES.register("goo_fog", GooParticles::colorParticleType);

    /**
     * Creates a non-syncing ParticleType that carries RGB color data.
     *
     * @return the configured color particle type
     */
    private static ParticleType<ColorParticleOption> colorParticleType() {
        return new ParticleType<>(false) {
            @Override
            public MapCodec<ColorParticleOption> codec() {
                return ColorParticleOption.codec(this);
            }

            @Override
            public StreamCodec<? super RegistryFriendlyByteBuf, ColorParticleOption> streamCodec() {
                return ColorParticleOption.streamCodec(this);
            }
        };
    }

    /**
     * Creates a non-syncing ParticleType that carries a goo type.
     *
     * @return the configured goo drip particle type
     */
    private static ParticleType<GooDripParticleOptions> gooDripParticleType() {
        return new ParticleType<>(false) {
            @Override
            public MapCodec<GooDripParticleOptions> codec() {
                return GooDripParticleOptions.codec(this);
            }

            @Override
            public StreamCodec<? super RegistryFriendlyByteBuf, GooDripParticleOptions> streamCodec() {
                return GooDripParticleOptions.streamCodec(this);
            }
        };
    }
}
