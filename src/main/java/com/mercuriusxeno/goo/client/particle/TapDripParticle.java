package com.mercuriusxeno.goo.client.particle;

import com.mercuriusxeno.goo.registry.GooParticles;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.util.RandomSource;
import org.jspecify.annotations.Nullable;

/**
 * The tap-drip: a square drop falling straight down from a tap's spigot, and
 * its square splat (decision tap-drip-own-square-particles).
 */
public final class TapDripParticle {

    private TapDripParticle() {
    }

    /** Provider for the falling tap-drip, spawned by an open tap. */
    public static class Provider extends DripParticle.FallProvider {

        /**
         * Creates a provider splatting as tap_drip_land.
         *
         * @param sprites the sprite set from the tap_drip definition
         */
        public Provider(SpriteSet sprites) {
            super(sprites, GooParticles.TAP_DRIP_LAND);
        }

        /**
         * Creates a falling tap-drip with no horizontal speed, whatever the packet carried.
         *
         * @param options the color particle data carrying RGB values
         * @param level the client level to spawn in
         * @param x the x spawn coordinate
         * @param y the y spawn coordinate
         * @param z the z spawn coordinate
         * @param xSpeed the x velocity, dropped
         * @param ySpeed the y velocity for the falling drip
         * @param zSpeed the z velocity, dropped
         * @param random the random source
         * @return the new falling drip particle
         */
        @Override
        public @Nullable Particle createParticle(
                ColorParticleOption options, ClientLevel level,
                double x, double y, double z,
                double xSpeed, double ySpeed, double zSpeed,
                RandomSource random) {
            return super.createParticle(options, level, x, y, z, 0.0, ySpeed, 0.0, random);
        }
    }

    /** Provider for the tap-drip's square ground splat. */
    public static class LandProvider extends DripParticle.LandProvider {

        /**
         * Creates a land provider.
         *
         * @param sprites the sprite set from the tap_drip_land definition
         */
        public LandProvider(SpriteSet sprites) {
            super(sprites);
        }
    }
}
