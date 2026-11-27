package com.mercuriusxeno.goo.client.particle;

import com.mercuriusxeno.goo.client.ability.SprayCone;
import com.mercuriusxeno.goo.throwing.StreamCone;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
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

    /** The most ticks a flake lives. */
    public static final int LONGEST_LIFE = BASE_LIFETIME + LIFETIME_VARIANCE - 1;

    private final float flutterPhase;
    private final float spin;
    /** The cone the flake keeps inside, or null for a flake free to drift anywhere. */
    private @Nullable SprayCone bound;

    /**
     * Where a flake stands and how fast it flies.
     *
     * @param at       its position in the world
     * @param velocity its motion a tick
     */
    public record Flight(Vec3 at, Vec3 velocity) {
    }

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

    /**
     * Keeps the flake inside a cone for the rest of its life.
     * decision spray-abilities-render-procedurally-bound-to-their-cone
     *
     * @param cone the cone it keeps inside, or null to let it drift anywhere
     */
    public void keepInside(@Nullable SprayCone cone) {
        this.bound = cone;
    }

    /**
     * How far a flake's flutter swings at an age: side to side, from its own phase.
     *
     * @param phase where its swing starts, in radians
     * @param age   ticks it has lived
     * @return the swing, -1 to 1
     */
    public static float swingAt(float phase, int age) {
        return Mth.sin(phase + age * FLUTTER_RATE);
    }

    /**
     * A flake's flight one tick on: its flutter nudges it, it moves, and the wind's friction slows it.
     *
     * @param flight where it stands and how fast it flies
     * @param swing  its flutter's swing this tick
     * @param bound  the cone it keeps inside, or null for none
     * @return its flight a tick later
     */
    public static Flight flown(Flight flight, float swing, @Nullable SprayCone bound) {
        Vec3 velocity = flight.velocity().add(swing * FLUTTER, 0, -swing * FLUTTER);
        Vec3 at = flight.at().add(velocity);
        if (bound != null) {
            // decision spray-abilities-render-procedurally-bound-to-their-cone: a drift past the rim or the reach keeps to it
            at = StreamCone.pulledInside(bound.apex(), bound.axis(), bound.reach(), bound.shape(), at);
        }
        return new Flight(at, velocity.scale(WIND_FRICTION));
    }

    /** Flutters, turns and fades the flake over its life. */
    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        if (this.age++ >= this.lifetime) {
            this.remove();
            return;
        }
        Flight flight = flown(new Flight(new Vec3(x, y, z), new Vec3(xd, yd, zd)), swingAt(flutterPhase, age - 1),
                bound);
        this.setPos(flight.at().x, flight.at().y, flight.at().z);
        this.xd = flight.velocity().x;
        this.yd = flight.velocity().y;
        this.zd = flight.velocity().z;
        this.oRoll = this.roll;
        this.roll += spin;
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
