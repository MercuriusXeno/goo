package com.mercuriusxeno.goo.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;
import org.jspecify.annotations.Nullable;

/**
 * A mote of glow's warm yellow-white light that rises slowly out of Laser's
 * beam on the velocity it was given and fades out over its life.
 * sunbeam-sheds-motes-rising-from-the-beam
 */
public final class LightMoteParticle extends DriftingGlowParticle {

    private static final float START_ALPHA = 0.9f;
    /** Glow's warm yellow-white, the beam's own core color. */
    private static final Look MOTE = new Look(0.97f, 0.02f, 24, 16, 0.035f, 0.025f,
            new float[] {1.0f, 0.96f, 0.78f}, START_ALPHA);

    private LightMoteParticle(ClientLevel level, double[] position, double[] velocity, SpriteSet sprites) {
        super(level, position, velocity, sprites, MOTE);
    }

    /** Fades the mote toward nothing over its life. */
    @Override
    public void tick() {
        super.tick();
        this.alpha = START_ALPHA * (1f - lifeProgress());
    }

    /**
     * Creates light motes from the simple particle type, keeping the velocity
     * the sender gave each one.
     */
    public static class Provider implements ParticleProvider<SimpleParticleType> {

        private final SpriteSet sprites;

        /**
         * Creates a provider over the mote's sprite set.
         *
         * @param sprites the sprite set from light_mote.json
         */
        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public @Nullable LightMoteParticle createParticle(SimpleParticleType type, ClientLevel level,
                                                          double x, double y, double z,
                                                          double xSpeed, double ySpeed, double zSpeed,
                                                          RandomSource random) {
            return new LightMoteParticle(level, new double[] {x, y, z}, new double[] {xSpeed, ySpeed, zSpeed},
                    sprites);
        }
    }
}
