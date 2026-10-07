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
 * A soft puff of vital fog: a wide, faint radial glow that drifts with the
 * velocity it was given, swelling as it goes and fading in then out, so many
 * of them overlapping read as one fog rather than as dots. Vitality fills
 * its cone with them while held.
 * vitality-waves-regenerate-and-court
 */
public final class VitalFogParticle extends SingleQuadParticle {

    private static final float COLLISION_SIZE = 0.01f;
    private static final float FRICTION = 0.92f;
    private static final int BASE_LIFETIME = 18;
    private static final int LIFETIME_VARIANCE = 10;
    /** Quad size the puff starts at, and the share it swells by over its life. */
    private static final float BASE_QUAD_SIZE = 0.35f;
    private static final float QUAD_SIZE_VARIANCE = 0.2f;
    private static final float SWELL = 1.5f;
    /** Peak opacity, faint so overlapping puffs build to a fog. */
    private static final float PEAK_ALPHA = 0.12f;
    /** Vital pink. */
    private static final float RED = 1.0f;
    private static final float GREEN = 0.5f;
    private static final float BLUE = 0.65f;

    private VitalFogParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz,
                             SpriteSet sprites) {
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
        this.alpha = 0f;
    }

    /** Drifts the puff and fades it in over the first half of its life and out over the second. */
    @Override
    public void tick() {
        super.tick();
        float progress = (float) this.age / this.lifetime;
        this.alpha = PEAK_ALPHA * (float) Math.sin(Math.PI * Math.min(1f, progress));
    }

    /**
     * Swells over its life.
     *
     * @param partialTick the partial tick for interpolation
     * @return the scaled quad size for this frame
     */
    @Override
    public float getQuadSize(float partialTick) {
        float progress = Math.min(1f, (this.age + partialTick) / this.lifetime);
        return this.quadSize * (1f + SWELL * progress);
    }

    /**
     * Fog blends over what lies behind it.
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
     * Creates fog puffs, keeping the velocity the spawner gave each one.
     */
    public static class Provider implements ParticleProvider<SimpleParticleType> {

        private final SpriteSet sprites;

        /**
         * Creates a provider over the fog's sprite set.
         *
         * @param sprites the sprite set from vital_fog.json
         */
        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public @Nullable VitalFogParticle createParticle(SimpleParticleType type, ClientLevel level,
                                                         double x, double y, double z,
                                                         double xSpeed, double ySpeed, double zSpeed,
                                                         RandomSource random) {
            return new VitalFogParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, sprites);
        }
    }
}
