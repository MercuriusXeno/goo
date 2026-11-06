package com.mercuriusxeno.goo.client.particle;

import com.mercuriusxeno.goo.client.GooSubmitter;
import com.mercuriusxeno.goo.client.ability.FireSpray;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;
import org.jspecify.annotations.Nullable;

/**
 * A lick of flame in Flames' fire: it flies the speed {@link FireSpray} threw
 * it at, slowing by the friction the spray's reach is reckoned with, swells
 * as it leaves the spout, rises, then shrinks and fades out, burning at full
 * brightness. A block stops it where it meets it.
 * flames-draws-as-particle-style-fire
 */
public final class FireLickParticle extends SingleQuadParticle {

    /** Negative gravity: the lick rises as it flies. */
    private static final float RISE = -0.12f;
    private static final float BASE_QUAD_SIZE = 0.14f;
    private static final float QUAD_SIZE_VARIANCE = 0.1f;
    /** The share of its life a lick takes to swell to its full size. */
    private static final float SWELL_SHARE = 0.2f;
    /** The size a lick opens at, as a share of its full size. */
    private static final float OPENING_SIZE = 0.5f;
    /** The share of its life past which a lick fades out. */
    private static final float FADE_FROM = 0.6f;

    private FireLickParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz,
                             SpriteSet sprites) {
        super(level, x, y, z, sprites.get(level.getRandom()));
        this.xd = vx;
        this.yd = vy;
        this.zd = vz;
        this.gravity = RISE;
        this.friction = FireSpray.LICK_FRICTION;
        this.hasPhysics = true;
        RandomSource random = level.getRandom();
        int shortest = Math.round(FireSpray.LICK_LONGEST_LIFE * FireSpray.LICK_SHORTEST_SHARE);
        this.lifetime = shortest + random.nextInt(FireSpray.LICK_LONGEST_LIFE - shortest + 1);
        this.quadSize = BASE_QUAD_SIZE + random.nextFloat() * QUAD_SIZE_VARIANCE;
    }

    /** Flies, rises and fades the lick out over the last of its life. */
    @Override
    public void tick() {
        super.tick();
        float progress = (float) this.age / this.lifetime;
        this.alpha = progress < FADE_FROM ? 1f : Math.max(0f, (1f - progress) / (1f - FADE_FROM));
    }

    /**
     * Swells from the spout, then shrinks toward the end of its life.
     *
     * @param partialTick the partial tick
     * @return the quad's size this frame
     */
    @Override
    public float getQuadSize(float partialTick) {
        float progress = (this.age + partialTick) / this.lifetime;
        if (progress < SWELL_SHARE) {
            return this.quadSize * (OPENING_SIZE + (1f - OPENING_SIZE) * progress / SWELL_SHARE);
        }
        float shrinking = (progress - SWELL_SHARE) / (1f - SWELL_SHARE);
        return this.quadSize * (1f - shrinking * shrinking);
    }

    /**
     * Licks blend over what lies behind them as they fade.
     *
     * @return the translucent particle render layer
     */
    @Override
    public Layer getLayer() {
        return Layer.TRANSLUCENT;
    }

    /**
     * Fire burns at full brightness whatever the light where it flies.
     *
     * @param partialTick the partial tick
     * @return full block and sky light
     */
    @Override
    protected int getLightCoords(float partialTick) {
        return GooSubmitter.fullbrightLight();
    }

    /**
     * Makes licks from Flames' spray, keeping the speed it threw them at.
     */
    public static class Provider implements ParticleProvider<SimpleParticleType> {

        private final SpriteSet sprites;

        /**
         * Creates a provider over the flame sprite.
         *
         * @param sprites the sprite set
         */
        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public @Nullable FireLickParticle createParticle(SimpleParticleType type, ClientLevel level, double x,
                                                         double y, double z, double xSpeed, double ySpeed,
                                                         double zSpeed, RandomSource random) {
            return new FireLickParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, sprites);
        }
    }
}
