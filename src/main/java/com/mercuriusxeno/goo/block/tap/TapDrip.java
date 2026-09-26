package com.mercuriusxeno.goo.block.tap;

import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.block.canister.ICanisterHolder;
import com.mercuriusxeno.goo.registry.GooDripParticleOptions;
import com.mercuriusxeno.goo.registry.GooParticles;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * One drip of a tap: the draw from the tap's own canister slot, and the
 * particle an onlooker sees leave the spigot.
 */
public final class TapDrip {

    /**
     * Downward speed the drip particle leaves the spigot with, in blocks per tick.
     */
    static final double DRIP_LEAVE_SPEED = -0.05;

    private TapDrip() {
    }

    /**
     * Where a drip's particle goes: the server level in play, a recorder in tests.
     */
    @FunctionalInterface
    public interface ParticleSink {
        /**
         * Sends one particle from a point with a velocity.
         *
         * @param option   the particle
         * @param at       the starting point
         * @param velocity the starting velocity, in blocks per tick
         */
        void send(ParticleOptions option, Vec3 at, Vec3 velocity);
    }

    /**
     * The sink sending one particle per call to every player tracking the level.
     *
     * @param level the server level
     * @return the sink
     */
    static ParticleSink sinkOf(ServerLevel level) {
        return (option, at, velocity) -> level.sendParticles(option, at.x, at.y, at.z, 0,
                velocity.x, velocity.y, velocity.z, 1.0);
    }

    /**
     * Draws one drip of the goo the slot's canister holds, from that slot alone.
     *
     * @param holder the tap's canister holder
     * @param slot   the slot the tap drips from
     * @param volume the mB one drip draws at the tap's grade
     * @return the goo drawn, or null when the slot held no goo to draw
     */
    static @Nullable Drawn draw(ICanisterHolder holder, int slot, int volume) {
        ResourceKey<GooTypeDefinition> type = holder.getSlotGooType(slot);
        if (type == null) {
            return null;
        }
        int drawn = holder.extractGoo(slot, type, volume);
        return drawn > 0 ? new Drawn(type, drawn) : null;
    }

    /**
     * The goo one drip carries: less than the grade's volume when the
     * canister ran low.
     *
     * @param type   the goo type drawn
     * @param volume the mB drawn
     */
    record Drawn(ResourceKey<GooTypeDefinition> type, int volume) {
    }

    /**
     * Sends one tap-drip particle of the goo type leaving the spigot
     * straight down (decision particles-render-muted-goo-texture).
     *
     * @param sink    where the particle goes
     * @param gooType the goo type drawn
     * @param at      the spigot underside
     */
    public static void emit(ParticleSink sink, ResourceKey<GooTypeDefinition> gooType, Vec3 at) {
        emit(sink, GooParticles.TAP_DRIP.get(), gooType, at);
    }

    /**
     * Sends one drip particle of the goo type leaving the spigot straight down.
     *
     * @param sink     where the particle goes
     * @param particle the drip particle type
     * @param gooType  the goo type drawn
     * @param at       the spigot underside
     */
    static void emit(ParticleSink sink, ParticleType<GooDripParticleOptions> particle,
                     ResourceKey<GooTypeDefinition> gooType, Vec3 at) {
        send(sink, dripParticle(particle, gooType), at);
    }

    /**
     * @param particle the drip particle type
     * @param gooType  the goo type drawn
     * @return the drip particle naming that goo type, so the client draws its fluid sprite
     */
    static GooDripParticleOptions dripParticle(ParticleType<GooDripParticleOptions> particle,
                                               ResourceKey<GooTypeDefinition> gooType) {
        return new GooDripParticleOptions(particle, gooType);
    }

    /**
     * Sends one drip particle leaving the spigot straight down.
     *
     * @param sink     where the particle goes
     * @param particle the drip particle
     * @param at       the spigot underside
     */
    private static void send(ParticleSink sink, ParticleOptions particle, Vec3 at) {
        sink.send(particle, at, new Vec3(0.0, DRIP_LEAVE_SPEED, 0.0));
    }

    /**
     * Shows one due drip: at 1:1 it joins the pouring stream and sends no
     * particle; every slower grade sends its drip particle and pours no
     * stream (decision one-to-one-draws-a-stream).
     *
     * @param grade    the tap's drip grade
     * @param pour     the stream this drip would join
     * @param sink     where a drip particle goes
     * @param particle the drip particle
     * @param spigot   the spigot underside
     * @return the stream the tap pours, or null when the drip falls as a particle
     */
    static @Nullable TapStream release(TapDripGrade grade, TapStream pour, ParticleSink sink,
                                       ParticleOptions particle, Vec3 spigot) {
        if (grade.pours()) {
            return pour;
        }
        send(sink, particle, spigot);
        return null;
    }
}
