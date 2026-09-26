package com.mercuriusxeno.goo.client.particle;

import com.mercuriusxeno.goo.client.GooSubmitter;
import com.mercuriusxeno.goo.registry.GooParticles;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.util.ARGB;
import net.minecraft.util.RandomSource;

/**
 * The trail-drip: a 2x3 slime drip shed by thrown goo blobs mid-flight, and
 * its splat, each drawing its own sprite under the option's color
 * (decision tap-drip-own-square-particles).
 */
public final class TrailDripParticle {

    private TrailDripParticle() {
    }

    /**
     * @param sprites the particle definition's sprite set
     * @param options the color the drip was sent with
     * @return the whole first sprite under the option's color
     */
    private static DripLook look(SpriteSet sprites, ColorParticleOption options) {
        TextureAtlasSprite sprite = sprites.get(0, 1);
        return new DripLook(sprite, GooSubmitter.spriteUv(sprite),
                ARGB.colorFromFloat(1.0f, options.getRed(), options.getGreen(), options.getBlue()));
    }

    /** Provider for the falling trail-drip, splatting as trail_drip_land. */
    public static class Provider extends DripParticle.FallProvider<ColorParticleOption> {

        private final SpriteSet sprites;

        /**
         * @param sprites the sprite set from the trail_drip definition
         */
        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        protected DripLook look(ColorParticleOption options, RandomSource random) {
            return TrailDripParticle.look(sprites, options);
        }

        @Override
        protected SingleQuadParticle.Layer layer() {
            return SingleQuadParticle.Layer.TRANSLUCENT;
        }

        @Override
        protected ParticleOptions landOption(ColorParticleOption options) {
            return ColorParticleOption.create(GooParticles.TRAIL_DRIP_LAND.get(),
                    options.getRed(), options.getGreen(), options.getBlue());
        }
    }

    /** Provider for the trail-drip's ground splat. */
    public static class LandProvider extends DripParticle.LandProvider<ColorParticleOption> {

        private final SpriteSet sprites;

        /**
         * @param sprites the sprite set from the trail_drip_land definition
         */
        public LandProvider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        protected DripLook look(ColorParticleOption options, RandomSource random) {
            return TrailDripParticle.look(sprites, options);
        }

        @Override
        protected SingleQuadParticle.Layer layer() {
            return SingleQuadParticle.Layer.TRANSLUCENT;
        }
    }
}
