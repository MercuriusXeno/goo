package com.mercuriusxeno.goo.client.particle;

import com.mercuriusxeno.goo.client.GooSubmitter;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;
import org.jspecify.annotations.Nullable;

/**
 * A healing star: bonemeal's green star drawn in vital pink, rising a little
 * and fading where something was healed.
 * vitality-waves-regenerate-and-court
 */
public final class VitalStarParticle extends SingleQuadParticle {

    private static final float COLLISION_SIZE = 0.02f;
    private static final double RISE = 0.02;
    private static final int BASE_LIFETIME = 14;
    private static final int LIFETIME_VARIANCE = 8;
    private static final float QUAD_SIZE = 0.1f;
    /** Full opacity until half its life, then fading to nothing. */
    private static final float FADE_SPEED = 2f;
    /** Vital pink, as bright as bonemeal's green. */
    private static final float RED = 1.0f;
    private static final float GREEN = 0.42f;
    private static final float BLUE = 0.7f;

    private VitalStarParticle(ClientLevel level, double x, double y, double z, SpriteSet sprites) {
        super(level, x, y, z, sprites.get(0, 1));
        this.xd = 0;
        this.yd = RISE;
        this.zd = 0;
        this.setSize(COLLISION_SIZE, COLLISION_SIZE);
        this.gravity = 0f;
        this.hasPhysics = false;
        RandomSource random = level.getRandom();
        this.lifetime = BASE_LIFETIME + random.nextInt(LIFETIME_VARIANCE);
        this.quadSize = QUAD_SIZE;
        this.rCol = RED;
        this.gCol = GREEN;
        this.bCol = BLUE;
    }

    /** Rises and fades out over its last half. */
    @Override
    public void tick() {
        super.tick();
        float progress = (float) this.age / this.lifetime;
        this.alpha = Math.min(1f, FADE_SPEED * (1f - progress));
    }

    /**
     * Stars blend over what lies behind them.
     *
     * @return the translucent particle render layer
     */
    @Override
    public Layer getLayer() {
        return Layer.TRANSLUCENT;
    }

    /**
     * Glows at full brightness, as bonemeal's stars do.
     *
     * @param partialTick the partial tick
     * @return full block and sky light
     */
    @Override
    protected int getLightCoords(float partialTick) {
        return GooSubmitter.fullbrightLight();
    }

    /**
     * Creates healing stars.
     */
    public static class Provider implements ParticleProvider<SimpleParticleType> {

        private final SpriteSet sprites;

        /**
         * Creates a provider over the star's sprite set.
         *
         * @param sprites the sprite set from vital_star.json, vanilla's glint
         */
        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public @Nullable VitalStarParticle createParticle(SimpleParticleType type, ClientLevel level,
                                                          double x, double y, double z,
                                                          double xSpeed, double ySpeed, double zSpeed,
                                                          RandomSource random) {
            return new VitalStarParticle(level, x, y, z, sprites);
        }
    }
}
