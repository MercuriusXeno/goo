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
 * A glowing ember in Flames' fire: thrown slower than the licks, it drags to
 * a drift, rises out of the fire and flickers as it fades, burning at full
 * brightness. A block stops it where it meets it.
 * flames-draws-as-particle-style-fire
 */
public final class FireEmberParticle extends SingleQuadParticle {

    /** Negative gravity: the ember rises as it slows. */
    private static final float RISE = -0.3f;
    private static final float DRIFT_FRICTION = 0.9f;
    private static final int BASE_LIFETIME = 20;
    private static final int LIFETIME_VARIANCE = 16;
    private static final float BASE_QUAD_SIZE = 0.035f;
    private static final float QUAD_SIZE_VARIANCE = 0.025f;
    /** The most an ember's glow dips on a flickering tick. */
    private static final float FLICKER = 0.35f;
    /** The green an ember cools toward as it fades, from a yellow-orange to a deep orange. */
    private static final float HOT_GREEN = 0.75f;
    private static final float COOL_GREEN = 0.35f;
    private static final float BLUE = 0.1f;

    private FireEmberParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz,
                              SpriteSet sprites) {
        super(level, x, y, z, sprites.get(level.getRandom()));
        this.xd = vx;
        this.yd = vy;
        this.zd = vz;
        this.gravity = RISE;
        this.friction = DRIFT_FRICTION;
        this.hasPhysics = true;
        RandomSource random = level.getRandom();
        this.lifetime = BASE_LIFETIME + random.nextInt(LIFETIME_VARIANCE);
        this.quadSize = BASE_QUAD_SIZE + random.nextFloat() * QUAD_SIZE_VARIANCE;
        this.rCol = 1f;
        this.gCol = HOT_GREEN;
        this.bCol = BLUE;
    }

    /** Rises, cools from yellow-orange to deep orange and flickers out over its life. */
    @Override
    public void tick() {
        super.tick();
        float left = 1f - (float) this.age / this.lifetime;
        this.gCol = COOL_GREEN + (HOT_GREEN - COOL_GREEN) * left;
        this.alpha = Math.max(0f, left * (1f - this.random.nextFloat() * FLICKER));
    }

    /**
     * Embers blend over what lies behind them as they fade.
     *
     * @return the translucent particle render layer
     */
    @Override
    public Layer getLayer() {
        return Layer.TRANSLUCENT;
    }

    /**
     * Embers glow at full brightness whatever the light where they drift.
     *
     * @param partialTick the partial tick
     * @return full block and sky light
     */
    @Override
    protected int getLightCoords(float partialTick) {
        return GooSubmitter.fullbrightLight();
    }

    /**
     * Makes embers from Flames' spray, keeping the speed it threw them at.
     */
    public static class Provider implements ParticleProvider<SimpleParticleType> {

        private final SpriteSet sprites;

        /**
         * Creates a provider over the ember sprite.
         *
         * @param sprites the sprite set
         */
        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public @Nullable FireEmberParticle createParticle(SimpleParticleType type, ClientLevel level, double x,
                                                          double y, double z, double xSpeed, double ySpeed,
                                                          double zSpeed, RandomSource random) {
            return new FireEmberParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, sprites);
        }
    }
}
