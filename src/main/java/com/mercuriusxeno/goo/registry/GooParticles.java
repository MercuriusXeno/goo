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
     * A drifting mote of spores, the particle Mycosis sprays along its cone
     * and bursts from a spored corpse (decision mycosis-spore-stream-buds-and-poisons).
     */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> SPORE =
        PARTICLE_TYPES.register("spore", () -> new SimpleParticleType(false));

    /**
     * A maroon gnat darting in Decay's swarm, the particle its stream sprays
     * along the cone (decision decay-gnats-degrade-each-block-once).
     */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> GNAT =
        PARTICLE_TYPES.register("gnat", () -> new SimpleParticleType(false));

    /**
     * The particle a ability block's explosion names in place of vanilla's
     * explosion particles; its client provider spawns nothing, so the goo
     * type's own burnout explosion is the one seen (decision
     * elemental-explosion-per-type).
     */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> SILENT_BLAST =
        PARTICLE_TYPES.register("silent_blast", () -> new SimpleParticleType(false));

    /** Color-tinted bubble particle spawned during goo extraction. */
    public static final DeferredHolder<ParticleType<?>, ParticleType<ColorParticleOption>> GOO_BUBBLE =
        PARTICLE_TYPES.register("goo_bubble", GooParticles::colorParticleType);

    /** The trail-drip: 2x3 slime drip shed by thrown goo mid-flight. */
    public static final DeferredHolder<ParticleType<?>, ParticleType<ColorParticleOption>> TRAIL_DRIP =
        PARTICLE_TYPES.register("trail_drip", GooParticles::colorParticleType);

    /** Brief splat when a trail-drip hits the ground. */
    public static final DeferredHolder<ParticleType<?>, ParticleType<ColorParticleOption>> TRAIL_DRIP_LAND =
        PARTICLE_TYPES.register("trail_drip_land", GooParticles::colorParticleType);

    /**
     * The splat-drip: the trail-drip's drop, hanging from a goo splat on a
     * struck mob before it falls (decision splat-holds-then-dissolves-dripping).
     */
    public static final DeferredHolder<ParticleType<?>, ParticleType<ColorParticleOption>> SPLAT_DRIP =
        PARTICLE_TYPES.register("splat_drip", GooParticles::colorParticleType);

    /** The tap-drip: square drop falling straight down from a tap's spigot. */
    public static final DeferredHolder<ParticleType<?>, ParticleType<GooDripParticleOptions>> TAP_DRIP =
        PARTICLE_TYPES.register("tap_drip", GooParticles::gooDripParticleType);

    /** Square splat when a tap-drip hits the ground. */
    public static final DeferredHolder<ParticleType<?>, ParticleType<GooDripParticleOptions>> TAP_DRIP_LAND =
        PARTICLE_TYPES.register("tap_drip_land", GooParticles::gooDripParticleType);

    /** Radial gradient fog puff for goo flight trails. */
    public static final DeferredHolder<ParticleType<?>, ParticleType<ColorParticleOption>> GOO_FOG =
        PARTICLE_TYPES.register("goo_fog", GooParticles::colorParticleType);

    /**
     * The restore mote: a soft glow drifting upward, sprayed along a
     * vitality stream and its restoration wave rings.
     * vitality-waves-regenerate-and-court
     */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> RESTORE_MOTE =
        PARTICLE_TYPES.register("restore_mote", () -> new SimpleParticleType(false));

    /**
     * The vital mote: a mote of vital goo homing on a target the client
     * reads each tick: Reserve's life drawn into the glove, and Vitality's
     * goo homing from the glove onto each thing it heals.
     * reserve-hearts-sit-behind-the-bar
     */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> VITAL_MOTE =
        PARTICLE_TYPES.register("vital_mote", () -> new SimpleParticleType(false));

    /**
     * The vital fog: a faint pink puff, many of which fill Vitality's cone
     * while held.
     * vitality-waves-regenerate-and-court
     */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> VITAL_FOG =
        PARTICLE_TYPES.register("vital_fog", () -> new SimpleParticleType(false));

    /**
     * The vital star: bonemeal's star in vital pink, played where Vitality heals.
     * vitality-waves-regenerate-and-court
     */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> VITAL_STAR =
        PARTICLE_TYPES.register("vital_star", () -> new SimpleParticleType(false));

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
