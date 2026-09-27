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

    /** The drop's half extent: a 2-pixel square, narrower than the 4-pixel spigot. */
    static final float DROP_HALF_SIZE = 1f / 16f;

    /** The drop's patch of the fluid sprite: 2 pixels, the drop's width at native scale. */
    static final int DROP_PATCH_PIXELS = 2;

    /** The splat's share of its quad: the 4-pixel square the splat sprite painted on its 8-pixel canvas. */
    static final float SPLAT_QUAD_SHARE = 0.5f;

    /** The splat's patch of the fluid sprite: 4 pixels, the painted square it replaces. */
    static final int SPLAT_PATCH_PIXELS = 4;

    private TapDripParticle() {
    }

    /**
     * @param options     the goo type the drip carries
     * @param patchPixels the patch's width in sprite pixels
     * @param random      picks where on the sprite the patch sits
     * @return a muted patch of the type's still fluid sprite
     */
    private static DripLook look(GooDripParticleOptions options, int patchPixels, RandomSource random) {
        return TapDripLook.of(options.gooType(), GooSubmitter::fluidSprite, GooSubmitter::fluidTint,
                ClientGooTypes::color, patchPixels, random);
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
            return TapDripParticle.look(options, DROP_PATCH_PIXELS, random);
        }

        @Override
        protected SingleQuadParticle.Layer layer() {
            return SingleQuadParticle.Layer.TRANSLUCENT_TERRAIN;
        }

        @Override
        protected float quadHalfSize(float randomHalfSize) {
            return DROP_HALF_SIZE;
        }

        @Override
        protected ParticleOptions landOption(GooDripParticleOptions options) {
            return new GooDripParticleOptions(GooParticles.TAP_DRIP_LAND.get(), options.gooType());
        }

        /**
         * Creates a falling tap-drip hanging from the spigot with no horizontal
         * speed, whatever the packet carried; where the drop has no room to
         * hang clear of the surface, spawns its splat on that surface instead
         * (decision diagnose-then-fix-drip-z-fighting).
         *
         * @param options the goo type the drip carries
         * @param level the client level to spawn in
         * @param x the x spawn coordinate
         * @param y the spigot underside
         * @param z the z spawn coordinate
         * @param xSpeed the x velocity, dropped
         * @param ySpeed the y velocity for the falling drip
         * @param zSpeed the z velocity, dropped
         * @param random the random source
         * @return the new falling drip particle, or null when the splat stands in for it
         */
        @Override
        public @Nullable Particle createParticle(
                GooDripParticleOptions options, ClientLevel level,
                double x, double y, double z,
                double xSpeed, double ySpeed, double zSpeed,
                RandomSource random) {
            double room = DripParticle.roomBelow(level, x, y, z, DripQuadPlacement.hangingDrop(DROP_HALF_SIZE));
            if (!DripQuadPlacement.dropFits(room, DROP_HALF_SIZE)) {
                level.addParticle(landOption(options), x, y - room, z, 0.0, 0.0, 0.0);
                return null;
            }
            return super.createParticle(options, level, x, DripQuadPlacement.hangingSpawnY(y, DROP_HALF_SIZE), z,
                    0.0, ySpeed, 0.0, random);
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
            return TapDripParticle.look(options, SPLAT_PATCH_PIXELS, random);
        }

        @Override
        protected SingleQuadParticle.Layer layer() {
            return SingleQuadParticle.Layer.TRANSLUCENT_TERRAIN;
        }

        @Override
        protected float quadHalfSize(float rolledHalfSize) {
            return rolledHalfSize * SPLAT_QUAD_SHARE;
        }
    }
}
