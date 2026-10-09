package com.mercuriusxeno.goo.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;
import org.jspecify.annotations.Nullable;

/**
 * A gnat in Decay's swarm: it flies out with the speed the server sprayed
 * it with, then darts in a new direction every tick as it slows, so the
 * cone fills with a buzzing maroon cloud rather than a jet; it hangs
 * weightless and fades out over two seconds or so
 * (decision decay-gnats-degrade-each-block-once).
 */
public final class GnatParticle extends SingleQuadParticle {

    private static final float SWARM_FRICTION = 0.85f;
    /** The widest a gnat's velocity swerves each tick on each axis, half of it either way. */
    private static final double DART_SPAN = 0.08;
    private static final int BASE_LIFETIME = 30;
    private static final int LIFETIME_VARIANCE = 20;
    private static final float BASE_QUAD_SIZE = 0.06f;
    private static final float QUAD_SIZE_VARIANCE = 0.04f;
    private static final float START_ALPHA = 0.95f;
    private static final float MAROON_RED = 0.75f;
    private static final float MAROON_GREEN = 0.12f;
    private static final float MAROON_BLUE = 0.2f;
    private static final float DARK_RED = 0.5f;
    private static final float DARK_GREEN = 0.06f;
    private static final float DARK_BLUE = 0.11f;
    private static final double HALF = 0.5;

    private GnatParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz,
                         SpriteSet sprites) {
        super(level, x, y, z, sprites.get(0, 1));
        this.xd = vx;
        this.yd = vy;
        this.zd = vz;
        this.gravity = 0f;
        this.friction = SWARM_FRICTION;
        this.hasPhysics = true;
        tint(level.getRandom());
    }

    private void tint(RandomSource random) {
        float blend = random.nextFloat();
        this.lifetime = BASE_LIFETIME + random.nextInt(LIFETIME_VARIANCE);
        this.quadSize = BASE_QUAD_SIZE + random.nextFloat() * QUAD_SIZE_VARIANCE;
        this.rCol = MAROON_RED + (DARK_RED - MAROON_RED) * blend;
        this.gCol = MAROON_GREEN + (DARK_GREEN - MAROON_GREEN) * blend;
        this.bCol = MAROON_BLUE + (DARK_BLUE - MAROON_BLUE) * blend;
        this.alpha = START_ALPHA;
    }

    /** Darts the gnat a little off its heading and fades it over its life. */
    @Override
    public void tick() {
        RandomSource random = this.random;
        this.xd += (random.nextDouble() - HALF) * DART_SPAN;
        this.yd += (random.nextDouble() - HALF) * DART_SPAN;
        this.zd += (random.nextDouble() - HALF) * DART_SPAN;
        super.tick();
        this.alpha = START_ALPHA * (1f - (float) this.age / this.lifetime);
    }

    @Override
    public Layer getLayer() {
        return Layer.TRANSLUCENT;
    }

    /**
     * Makes gnats from the server's spray, keeping its speed.
     */
    public static class Provider implements ParticleProvider<SimpleParticleType> {

        private final SpriteSet sprites;

        /**
         * Creates a provider over the gnat sprite.
         *
         * @param sprites the sprite set
         */
        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public @Nullable GnatParticle createParticle(SimpleParticleType type, ClientLevel level, double x, double y,
                                                     double z, double xSpeed, double ySpeed, double zSpeed,
                                                     RandomSource random) {
            return new GnatParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, sprites);
        }
    }
}
