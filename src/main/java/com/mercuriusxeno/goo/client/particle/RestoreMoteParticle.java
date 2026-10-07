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
 * A soft glow that drifts upward from where it spawns, carried along by the
 * velocity it was given, and swells then fades over its life; vitality's
 * stream and wave rings spray it in place of vanilla heal particles.
 * vitality-waves-regenerate-and-court
 */
public final class RestoreMoteParticle extends SingleQuadParticle {

    /** Lift each tick on top of the spawn velocity, in blocks. */
    private static final double RISE_PER_TICK = 0.012;
    private static final float FRICTION = 0.9f;
    private static final float COLLISION_SIZE = 0.02f;
    private static final int BASE_LIFETIME = 18;
    private static final int LIFETIME_VARIANCE = 12;
    private static final float BASE_QUAD_SIZE = 0.06f;
    private static final float QUAD_SIZE_VARIANCE = 0.04f;
    private static final float START_ALPHA = 0.85f;
    /** A warm rose tint, lighter than vital goo's wheel color so the glow reads as healing, not blood. */
    private static final float RED = 1.0f;
    private static final float GREEN = 0.62f;
    private static final float BLUE = 0.6f;

    private RestoreMoteParticle(ClientLevel level, double x, double y, double z,
                                double vx, double vy, double vz, SpriteSet sprites) {
        super(level, x, y, z, sprites.get(0, 1));
        this.xd = vx;
        this.yd = vy;
        this.zd = vz;
        this.setSize(COLLISION_SIZE, COLLISION_SIZE);
        this.gravity = 0f;
        this.friction = FRICTION;
        this.hasPhysics = false;
        RandomSource random = level.getRandom();
        this.lifetime = BASE_LIFETIME + random.nextInt(LIFETIME_VARIANCE);
        this.quadSize = BASE_QUAD_SIZE + random.nextFloat() * QUAD_SIZE_VARIANCE;
        this.rCol = RED;
        this.gCol = GREEN;
        this.bCol = BLUE;
        this.alpha = START_ALPHA;
    }

    /** Lifts the mote and fades it toward nothing at the end of its life. */
    @Override
    public void tick() {
        this.yd += RISE_PER_TICK;
        super.tick();
        float progress = (float) this.age / this.lifetime;
        this.alpha = START_ALPHA * (1f - progress * progress);
    }

    /**
     * Swells over the first half of its life and shrinks over the second.
     *
     * @param partialTick the partial tick for interpolation
     * @return the scaled quad size for this frame
     */
    @Override
    public float getQuadSize(float partialTick) {
        float progress = (this.age + partialTick) / this.lifetime;
        return this.quadSize * (float) Math.sin(Math.PI * Math.min(1f, progress));
    }

    /**
     * Motes blend over what lies behind them.
     *
     * @return the translucent particle render layer
     */
    @Override
    public Layer getLayer() {
        return Layer.TRANSLUCENT;
    }

    /**
     * Glows at full brightness whatever the light where it floats.
     *
     * @param partialTick the partial tick
     * @return full block and sky light
     */
    @Override
    protected int getLightCoords(float partialTick) {
        return GooSubmitter.fullbrightLight();
    }

    /**
     * Creates restore motes from the simple particle type, keeping the
     * velocity the sender gave each one.
     */
    public static class Provider implements ParticleProvider<SimpleParticleType> {

        private final SpriteSet sprites;

        /**
         * Creates a provider over the mote's sprite set.
         *
         * @param sprites the sprite set from restore_mote.json
         */
        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public @Nullable RestoreMoteParticle createParticle(SimpleParticleType type, ClientLevel level,
                                                            double x, double y, double z,
                                                            double xSpeed, double ySpeed, double zSpeed,
                                                            RandomSource random) {
            return new RestoreMoteParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, sprites);
        }
    }
}
