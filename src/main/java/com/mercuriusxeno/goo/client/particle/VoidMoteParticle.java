package com.mercuriusxeno.goo.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import org.jspecify.annotations.Nullable;

/**
 * A twinkling speck of void, deep violet against the pocket's black, rising
 * slowly on the velocity it was given: vanilla's glitter sprites, as the end
 * rod sheds, shrinking as it ages, its light swelling and dimming at its own
 * pace so a wall of them glints rather than glows, fading out over its long
 * life. Specks rise along each wall of an Outsider room as it gains a full
 * block; the operator found round motes rising in even rows read as balls
 * on a grid.
 * full-block-growth-wubs-and-raises-void-motes
 */
public final class VoidMoteParticle extends DriftingGlowParticle {

    private static final float START_ALPHA = 0.95f;
    /** Slow to lose its rise, long lived and small, in the void's violet. */
    private static final Look SPECK = new Look(0.99f, 0.02f, 40, 30, 0.05f, 0.05f,
            new float[] {0.62f, 0.38f, 1.0f}, START_ALPHA);
    /** The slowest and fastest a speck twinkles, radians of its glint a tick. */
    private static final float SLOWEST_TWINKLE = 0.25f;
    private static final float TWINKLE_SPREAD = 0.45f;
    /** The share of its light a speck keeps at the dimmest of its twinkle. */
    private static final float DIMMEST = 0.25f;

    private final SpriteSet sprites;
    private final float twinkle;
    private final float phase;

    private VoidMoteParticle(ClientLevel level, double[] position, double[] velocity, SpriteSet sprites) {
        super(level, position, velocity, sprites, SPECK);
        this.sprites = sprites;
        RandomSource random = level.getRandom();
        this.twinkle = SLOWEST_TWINKLE + random.nextFloat() * TWINKLE_SPREAD;
        this.phase = random.nextFloat() * Mth.TWO_PI;
        this.setSpriteFromAge(sprites);
    }

    /** Shrinks the speck through the glitter sprites, twinkles it and fades it toward nothing over its life. */
    @Override
    public void tick() {
        super.tick();
        this.setSpriteFromAge(sprites);
        float glint = DIMMEST + (1f - DIMMEST) * Math.abs(Mth.sin(this.age * twinkle + phase));
        this.alpha = START_ALPHA * (1f - lifeProgress()) * glint;
    }

    /**
     * Creates void specks from the simple particle type, keeping the velocity
     * the sender gave each one.
     */
    public static class Provider implements ParticleProvider<SimpleParticleType> {

        private final SpriteSet sprites;

        /**
         * Creates a provider over the speck's sprite set.
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
