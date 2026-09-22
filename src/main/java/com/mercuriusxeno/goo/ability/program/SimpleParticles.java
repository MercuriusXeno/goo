package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.Goo;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import java.util.Optional;

/**
 * The world side of resolving a {@link ParticlesStep}'s id, shared by
 * every host that sends particles: a step names a particle that needs no
 * options, and an id the registry lacks or that names a particle with
 * options is logged and skipped.
 */
final class SimpleParticles {

    private static final String LOG_UNKNOWN_PARTICLE =
            "Particles step names {}, which no registry holds as a particle without options";

    private SimpleParticles() {
    }

    /**
     * Resolves a particle id to a type that takes no options.
     *
     * @param id the particle type id
     * @return the particle, or empty after logging when none serves the id
     */
    static Optional<SimpleParticleType> resolve(Identifier id) {
        Optional<Holder.Reference<ParticleType<?>>> holder = BuiltInRegistries.PARTICLE_TYPE.get(id);
        if (holder.isPresent() && holder.get().value() instanceof SimpleParticleType particle) {
            return Optional.of(particle);
        }
        Goo.LOGGER.warn(LOG_UNKNOWN_PARTICLE, id);
        return Optional.empty();
    }
}
