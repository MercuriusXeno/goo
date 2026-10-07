package com.mercuriusxeno.goo.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;
import org.jspecify.annotations.Nullable;

/**
 * A mote of spores drifting in a Mycosis cloud: it carries the speed the
 * server sprayed it with, slows, sinks a little and fades, tinted between a
 * pale mushroom tan and a dusky violet.
 * mycosis-spore-stream-buds-and-poisons
 */
public final class SporeParticle extends SingleQuadParticle {

    private static final float DRIFT_GRAVITY = 0.02f;
    private static final float DRIFT_FRICTION = 0.9f;
    private static final int BASE_LIFETIME = 30;
    private static final int LIFETIME_VARIANCE = 20;
    private static final float BASE_QUAD_SIZE = 0.05f;
    private static final float QUAD_SIZE_VARIANCE = 0.04f;
    private static final float START_ALPHA = 0.85f;
    private static final float TAN_RED = 0.85f;
    private static final float TAN_GREEN = 0.78f;
    private static final float TAN_BLUE = 0.62f;
    private static final float VIOLET_RED = 0.55f;
    private static final float VIOLET_GREEN = 0.42f;
    private static final float VIOLET_BLUE = 0.6f;

    private SporeParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz,
                          SpriteSet sprites) {
        super(level, x, y, z, sprites.get(0, 1));
        this.xd = vx;
        this.yd = vy;
        this.zd = vz;
        this.gravity = DRIFT_GRAVITY;
        this.friction = DRIFT_FRICTION;
        this.hasPhysics = true;
        tint(level.getRandom());
    }

    private void tint(RandomSource random) {
        float blend = random.nextFloat();
        this.lifetime = BASE_LIFETIME + random.nextInt(LIFETIME_VARIANCE);
        this.quadSize = BASE_QUAD_SIZE + random.nextFloat() * QUAD_SIZE_VARIANCE;
        this.rCol = TAN_RED + (VIOLET_RED - TAN_RED) * blend;
        this.gCol = TAN_GREEN + (VIOLET_GREEN - TAN_GREEN) * blend;
        this.bCol = TAN_BLUE + (VIOLET_BLUE - TAN_BLUE) * blend;
        this.alpha = START_ALPHA;
    }

    /** Drifts and fades the mote out over its life. */
    @Override
    public void tick() {
        super.tick();
        this.alpha = START_ALPHA * (1f - (float) this.age / this.lifetime);
    }

    @Override
    public Layer getLayer() {
        return Layer.TRANSLUCENT;
    }

    /**
     * Makes spore motes from the server's spray, keeping its speed.
     */
    public static class Provider implements ParticleProvider<SimpleParticleType> {

        private final SpriteSet sprites;

        /**
         * Creates a provider over the spore sprite.
         *
         * @param sprites the sprite set
         */
        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public @Nullable SporeParticle createParticle(SimpleParticleType type, ClientLevel level, double x, double y,
                                                      double z, double xSpeed, double ySpeed, double zSpeed,
                                                      RandomSource random) {
            return new SporeParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, sprites);
        }
    }
}
