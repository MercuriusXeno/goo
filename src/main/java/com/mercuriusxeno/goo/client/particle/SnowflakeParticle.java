package com.mercuriusxeno.goo.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import org.jspecify.annotations.Nullable;

/**
 * A snowflake crystal flitting weightlessly in Cold's wind: it rides the
 * speed it was given, slowing, with no fall, fluttering side to side and
 * turning as it goes, white with a faint blue, fading at the end of its life.
 * cold-streams-wind-lines-and-snowflakes
 */
public final class SnowflakeParticle extends SingleQuadParticle {

    private static final float WIND_FRICTION = 0.94f;
    private static final int BASE_LIFETIME = 30;
    private static final int LIFETIME_VARIANCE = 20;
    private static final float BASE_QUAD_SIZE = 0.06f;
    private static final float QUAD_SIZE_VARIANCE = 0.04f;
    /** How hard the flutter nudges the flake each tick, and how fast it swings. */
    private static final double FLUTTER = 0.006;
    private static final float FLUTTER_RATE = 0.4f;
    /** How far the flake's turn each tick spans, either way about none, in radians. */
    private static final float SPIN_RANGE = 0.4f;
    private static final float HALF = 0.5f;
    private static final float BLUE_TINT = 0.94f;

    private final float flutterPhase;
    private final float spin;

    private SnowflakeParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz,
                              SpriteSet sprites) {
        super(level, x, y, z, sprites.get(0, 1));
        RandomSource random = level.getRandom();
        this.xd = vx;
        this.yd = vy;
        this.zd = vz;
        this.gravity = 0f;
        this.friction = WIND_FRICTION;
        this.hasPhysics = false;
        this.lifetime = BASE_LIFETIME + random.nextInt(LIFETIME_VARIANCE);
        this.quadSize = BASE_QUAD_SIZE + random.nextFloat() * QUAD_SIZE_VARIANCE;
        this.rCol = BLUE_TINT;
        this.gCol = BLUE_TINT + (1f - BLUE_TINT) * random.nextFloat();
        this.bCol = 1f;
        this.flutterPhase = random.nextFloat() * Mth.TWO_PI;
        this.spin = (random.nextFloat() - HALF) * SPIN_RANGE;
    }

    /** Flutters, turns and fades the flake over its life. */
    @Override
    public void tick() {
        float swing = Mth.sin(flutterPhase + age * FLUTTER_RATE);
        this.xd += swing * FLUTTER;
        this.zd -= swing * FLUTTER;
        this.oRoll = this.roll;
        this.roll += spin;
        super.tick();
        this.alpha = 1f - (float) this.age / this.lifetime;
    }

    @Override
    public Layer getLayer() {
        return Layer.TRANSLUCENT;
    }

    /**
     * Makes snowflakes at the speed they were sprayed with.
     */
    public static class Provider implements ParticleProvider<SimpleParticleType> {

        private final SpriteSet sprites;

        /**
         * Creates a provider over the snowflake sprite.
         *
         * @param sprites the sprite set
         */
        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public @Nullable SnowflakeParticle createParticle(SimpleParticleType type, ClientLevel level, double x,
                                                          double y, double z, double xSpeed, double ySpeed,
                                                          double zSpeed, RandomSource random) {
            return new SnowflakeParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, sprites);
        }
    }
}
