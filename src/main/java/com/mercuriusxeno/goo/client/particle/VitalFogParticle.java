package com.mercuriusxeno.goo.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;
import org.jspecify.annotations.Nullable;

/**
 * A soft puff of jelly fog: a wide, faint radial glow that drifts with the
 * velocity it was given, swelling as it goes and fading in then out, so many
 * of them overlapping read as one fog rather than as dots. Jelly heal fills
 * its cone with them while held.
 * nourish-and-healing-ship-on-jelly
 */
public final class VitalFogParticle extends DriftingGlowParticle {

    /** The share a puff swells by over its life. */
    private static final float SWELL = 1.5f;
    /** Peak opacity, faint so overlapping puffs build to a fog. */
    private static final float PEAK_ALPHA = 0.12f;
    /** A wide puff in jelly amber, opening unseen and fading in. */
    private static final Look PUFF = new Look(0.92f, 0.01f, 18, 10, 0.35f, 0.2f,
            new float[] {1.0f, 0.7f, 0.3f}, 0f);

    private VitalFogParticle(ClientLevel level, double[] position, double[] velocity, SpriteSet sprites) {
        super(level, position, velocity, sprites, PUFF);
    }

    /** Drifts the puff and fades it in over the first half of its life and out over the second. */
    @Override
    public void tick() {
        super.tick();
        float progress = lifeProgress();
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
            return new VitalFogParticle(level, new double[] {x, y, z}, new double[] {xSpeed, ySpeed, zSpeed},
                    sprites);
        }
    }
}
