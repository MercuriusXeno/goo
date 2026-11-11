package com.mercuriusxeno.goo.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;
import org.jspecify.annotations.Nullable;

/**
 * A wisp of hex mist: a faint, wide puff in hex purple that drifts undragged
 * with the velocity it was given for a fixed flight, fading in quickly and out
 * as its flight ends, so a spawner aiming each one at the glove hand over that
 * flight sees it fade as it arrives. Drain fills its flat cone with them while
 * held, each puff its own size.
 * drain-casts-a-hex-mist-cone-toward-the-hand
 */
public final class HexMistParticle extends DriftingGlowParticle {

    /** Ticks every puff lives: its flight from where it spawns to the hand. */
    public static final int FLIGHT_TICKS = 24;
    /** Peak opacity, faint so overlapping puffs build to a mist. */
    private static final float PEAK_ALPHA = 0.16f;
    /** The share of its flight a puff fades in over. */
    private static final float FADE_IN_SHARE = 0.2f;
    /** A puff in the hex ailment's purple, keeping all its speed, of a random size, opening unseen. */
    private static final Look PUFF = new Look(1f, 0.01f, FLIGHT_TICKS, 1, 0.18f, 0.3f,
            new float[] {0.42f, 0.12f, 0.69f}, 0f);

    private HexMistParticle(ClientLevel level, double[] position, double[] velocity, SpriteSet sprites) {
        super(level, position, velocity, sprites, PUFF);
    }

    /** Drifts the puff, fading it in quickly and out as its flight ends. */
    @Override
    public void tick() {
        super.tick();
        float progress = Math.min(1f, lifeProgress());
        this.alpha = PEAK_ALPHA * Math.min(1f, progress / FADE_IN_SHARE) * (1f - progress);
    }

    /**
     * Creates hex mist puffs, keeping the velocity the spawner gave each one.
     */
    public static class Provider implements ParticleProvider<SimpleParticleType> {

        private final SpriteSet sprites;

        /**
         * Creates a provider over the mist's sprite set.
         *
         * @param sprites the sprite set from hex_mist.json
         */
        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public @Nullable HexMistParticle createParticle(SimpleParticleType type, ClientLevel level,
                                                        double x, double y, double z,
                                                        double xSpeed, double ySpeed, double zSpeed,
                                                        RandomSource random) {
            return new HexMistParticle(level, new double[] {x, y, z}, new double[] {xSpeed, ySpeed, zSpeed},
                    sprites);
        }
    }
}
