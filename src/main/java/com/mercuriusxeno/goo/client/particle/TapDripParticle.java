package com.mercuriusxeno.goo.client.particle;

import com.mercuriusxeno.goo.client.ClientGooTypes;
import com.mercuriusxeno.goo.client.GooSubmitter;
import com.mercuriusxeno.goo.registry.GooDripParticleOptions;
import com.mercuriusxeno.goo.registry.GooParticles;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.util.RandomSource;
import org.jspecify.annotations.Nullable;

/**
 * The tap-drip: a square drop falling straight down from a tap's spigot, and
 * its square splat, each drawing a muted patch of its goo type's fluid
 * sprite on the block atlas (decision particles-render-muted-goo-texture).
 * The definitions' own sprite sets stand registered but go undrawn.
 */
public final class TapDripParticle {

    private TapDripParticle() {
    }

    /**
     * @param options the goo type the drip carries
     * @param random  picks where on the sprite the patch sits
     * @return a muted patch of the type's still fluid sprite
     */
    private static DripLook look(GooDripParticleOptions options, RandomSource random) {
        return TapDripLook.of(options.gooType(), GooSubmitter::fluidSprite, GooSubmitter::fluidTint,
                ClientGooTypes::color, random);
    }

    /** Provider for the falling tap-drip, spawned by an open tap. */
    public static class Provider extends DripParticle.FallProvider<GooDripParticleOptions> {

        /**
         * @param sprites the sprite set from the tap_drip definition, undrawn
         */
        public Provider(SpriteSet sprites) {
            super();
        }

        @Override
        protected DripLook look(GooDripParticleOptions options, RandomSource random) {
            return TapDripParticle.look(options, random);
        }

        @Override
        protected SingleQuadParticle.Layer layer() {
            return SingleQuadParticle.Layer.TRANSLUCENT_TERRAIN;
        }

        @Override
        protected ParticleOptions landOption(GooDripParticleOptions options) {
            return new GooDripParticleOptions(GooParticles.TAP_DRIP_LAND.get(), options.gooType());
        }

        /**
         * Creates a falling tap-drip with no horizontal speed, whatever the packet carried.
         *
         * @param options the goo type the drip carries
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
                GooDripParticleOptions options, ClientLevel level,
                double x, double y, double z,
                double xSpeed, double ySpeed, double zSpeed,
                RandomSource random) {
            return super.createParticle(options, level, x, y, z, 0.0, ySpeed, 0.0, random);
        }
    }

    /** Provider for the tap-drip's square ground splat. */
    public static class LandProvider extends DripParticle.LandProvider<GooDripParticleOptions> {

        /**
         * @param sprites the sprite set from the tap_drip_land definition, undrawn
         */
        public LandProvider(SpriteSet sprites) {
            super();
        }

        @Override
        protected DripLook look(GooDripParticleOptions options, RandomSource random) {
            return TapDripParticle.look(options, random);
        }

        @Override
        protected SingleQuadParticle.Layer layer() {
            return SingleQuadParticle.Layer.TRANSLUCENT_TERRAIN;
        }
    }
}
