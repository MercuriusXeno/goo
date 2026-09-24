package com.mercuriusxeno.goo.block.tap;

import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.block.canister.ICanisterHolder;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * One drip of a tap: the draw from the tap's own canister slot, and the
 * particle an onlooker sees leave the spigot.
 */
final class TapDrip {

    /**
     * Volume one drip draws, in mB (decision drip-draws-one-mb-from-canister).
     */
    static final int DRIP_VOLUME = 1;
    /**
     * Downward speed the drip particle leaves the spigot with, in blocks per tick.
     */
    static final double DRIP_LEAVE_SPEED = -0.05;
    /**
     * Opaque alpha channel for the particle color.
     */
    private static final int OPAQUE_ALPHA = 0xFF000000;

    private TapDrip() {
    }

    /**
     * Where a drip's particle goes: the server level in play, a recorder in tests.
     */
    @FunctionalInterface
    interface ParticleSink {
        /**
         * Sends one particle from a point with a vertical speed.
         *
         * @param option    the particle
         * @param at        the starting point
         * @param fallSpeed the vertical speed, negative downward
         */
        void send(ColorParticleOption option, Vec3 at, double fallSpeed);
    }

    /**
     * The sink sending one particle per call to every player tracking the level.
     *
     * @param level the server level
     * @return the sink
     */
    static ParticleSink sinkOf(ServerLevel level) {
        return (option, at, fallSpeed) ->
                level.sendParticles(option, at.x, at.y, at.z, 0, 0.0, fallSpeed, 0.0, 1.0);
    }

    /**
     * Draws one drip of the goo the slot's canister holds, from that slot alone.
     *
     * @param holder the tap's canister holder
     * @param slot   the slot the tap drips from
     * @return the goo type drawn, or null when the slot held no goo to draw
     */
    static @Nullable ResourceKey<GooTypeDefinition> draw(ICanisterHolder holder, int slot) {
        ResourceKey<GooTypeDefinition> type = holder.getSlotGooType(slot);
        if (type == null) {
            return null;
        }
        return holder.extractGoo(slot, type, DRIP_VOLUME) > 0 ? type : null;
    }

    /**
     * Sends one drip particle of the type's color leaving the spigot downward.
     *
     * @param sink     where the particle goes
     * @param particle the drip particle type
     * @param rgb      the goo type's color
     * @param at       the spigot underside
     */
    static void emit(ParticleSink sink, ParticleType<ColorParticleOption> particle, int rgb, Vec3 at) {
        sink.send(ColorParticleOption.create(particle, rgb | OPAQUE_ALPHA), at, DRIP_LEAVE_SPEED);
    }
}
