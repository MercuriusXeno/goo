package com.mercuriusxeno.goo.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;
import org.jspecify.annotations.Nullable;

/**
 * A mote of void, deep violet against the pocket's black, floating slowly up
 * off the floor on the velocity it was given and fading out over its long
 * life, a line of them standing along each wall of an Outsider room as it
 * gains a full block.
 * full-block-growth-wubs-and-raises-void-motes
 */
public final class VoidMoteParticle extends DriftingGlowParticle {

    private static final float START_ALPHA = 0.85f;
    /** Slow to lose its rise, long lived and small, in the void's violet. */
    private static final Look MOTE = new Look(0.99f, 0.02f, 50, 20, 0.04f, 0.02f,
            new float[] {0.45f, 0.2f, 0.8f}, START_ALPHA);

    private VoidMoteParticle(ClientLevel level, double[] position, double[] velocity, SpriteSet sprites) {
        super(level, position, velocity, sprites, MOTE);
    }

    /** Fades the mote toward nothing over its life. */
    @Override
    public void tick() {
        super.tick();
        this.alpha = START_ALPHA * (1f - lifeProgress());
    }

    /**
     * Creates void motes from the simple particle type, keeping the velocity
     * the sender gave each one.
     */
    public static class Provider implements ParticleProvider<SimpleParticleType> {

        private final SpriteSet sprites;

        /**
         * Creates a provider over the mote's sprite set.
         *
         * @param sprites the sprite set from void_mote.json
         */
        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public @Nullable VoidMoteParticle createParticle(SimpleParticleType type, ClientLevel level,
                                                         double x, double y, double z,
                                                         double xSpeed, double ySpeed, double zSpeed,
                                                         RandomSource random) {
            return new VoidMoteParticle(level, new double[] {x, y, z}, new double[] {xSpeed, ySpeed, zSpeed},
                    sprites);
        }
    }
}
