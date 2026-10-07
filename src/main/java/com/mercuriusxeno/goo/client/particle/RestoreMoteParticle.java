package com.mercuriusxeno.goo.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleProvider;
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
public final class RestoreMoteParticle extends DriftingGlowParticle {

    /** Lift each tick on top of the spawn velocity, in blocks. */
    private static final double RISE_PER_TICK = 0.012;
    private static final float START_ALPHA = 0.85f;
    /** A warm rose tint, lighter than vital goo's wheel color so the glow reads as healing, not blood. */
    private static final Look MOTE = new Look(0.9f, 0.02f, 18, 12, 0.06f, 0.04f,
            new float[] {1.0f, 0.62f, 0.6f}, START_ALPHA);

    private RestoreMoteParticle(ClientLevel level, double[] position, double[] velocity, SpriteSet sprites) {
        super(level, position, velocity, sprites, MOTE);
    }

    /** Lifts the mote and fades it toward nothing at the end of its life. */
    @Override
    public void tick() {
        this.yd += RISE_PER_TICK;
        super.tick();
        float progress = lifeProgress();
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
            return new RestoreMoteParticle(level, new double[] {x, y, z}, new double[] {xSpeed, ySpeed, zSpeed},
                    sprites);
        }
    }
}
