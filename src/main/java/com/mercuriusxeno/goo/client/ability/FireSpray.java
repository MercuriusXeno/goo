package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.registry.GooParticles;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;

/**
 * Flames' fire as particles: each tick a spout pours, it throws licks of
 * flame and embers from its apex down its cone, the one emitter held Flames
 * and a heater prism both pour through. A lick flies out to the stream's
 * reach, rising as it shrinks and fades; an ember drifts slower and rises
 * longer. The spray widens with the cone, so the fire is dense at the spout
 * and thins toward its reach, and a block stops each particle where it meets
 * it. flames-draws-as-particle-style-fire
 */
public final class FireSpray {

    /** blaze_flames.json's reach, which a heater prism pours at. */
    static final double FLAMES_RANGE = 6;
    /** blaze_flames.json's cone, which a heater prism pours at. */
    static final double FLAMES_CONE_DEGREES = 20;

    /** The velocity a lick keeps each tick. */
    public static final float LICK_FRICTION = 0.96f;
    /** The most ticks a lick lives. */
    public static final int LICK_LONGEST_LIFE = 16;
    /** The fewest ticks a lick lives, as a share of the longest. */
    public static final float LICK_SHORTEST_SHARE = 0.5f;
    /** Blocks a lick flies over its longest life for each block a tick it starts at. */
    static final double LICK_REACH_PER_SPEED = (1 - Math.pow(LICK_FRICTION, LICK_LONGEST_LIFE)) / (1 - LICK_FRICTION);
    /** The slowest lick's speed, as a share of the speed that carries it to the reach. */
    static final double LICK_SLOWEST = 0.8;
    /** An ember's speed, as a share of a lick's, at its slowest and its fastest. */
    private static final double EMBER_SLOWEST = 0.25;
    private static final double EMBER_FASTEST = 0.6;
    private static final int LICKS_PER_TICK = 7;
    private static final int EMBERS_PER_TICK = 2;
    private static final double TWO_PI = Math.PI * 2;
    private static final double HALF = 0.5;
    private static final double NEAR_VERTICAL = 0.9;
    private static final Vec3 WORLD_UP = new Vec3(0, 1, 0);
    private static final Vec3 WORLD_EAST = new Vec3(1, 0, 0);

    private FireSpray() {
    }

    /**
     * Where fire pours from and how: the apex, the way it points, its reach and its cone.
     *
     * @param apex        where the fire pours from, in world coordinates
     * @param axis        the unit vector it pours along
     * @param range       blocks the fire reaches
     * @param coneDegrees the cone's full angle
     */
    public record Spout(Vec3 apex, Vec3 axis, double range, double coneDegrees) {
    }

    /** The two particles the fire throws. */
    enum Kind {
        /** A lick of flame flying out to the reach. */
        LICK,
        /** A glowing ember drifting up out of the fire. */
        EMBER
    }

    /**
     * One particle to throw this tick.
     *
     * @param kind     which particle
     * @param at       where it starts, in world coordinates
     * @param velocity its speed, in blocks a tick
     */
    record Spark(Kind kind, Vec3 at, Vec3 velocity) {
    }

    /**
     * Throws one tick's fire from a spout into the level.
     *
     * @param level the client level
     * @param spout the spout
     */
    public static void pour(ClientLevel level, Spout spout) {
        for (Spark spark : sparksOf(spout, level.getRandom())) {
            SimpleParticleType type = spark.kind() == Kind.LICK ? GooParticles.FIRE_LICK.get()
                    : GooParticles.FIRE_EMBER.get();
            level.addParticle(type, spark.at().x, spark.at().y, spark.at().z, spark.velocity().x,
                    spark.velocity().y, spark.velocity().z);
        }
    }

    /**
     * One tick's fire from a spout: licks and embers, each flying inside the
     * cone and starting somewhere along its first tick's flight, so the
     * stream pours unbroken rather than in a puff a tick.
     *
     * @param spout  the spout
     * @param random the random source
     * @return the particles to throw
     */
    static List<Spark> sparksOf(Spout spout, RandomSource random) {
        double reachSpeed = spout.range() / LICK_REACH_PER_SPEED;
        double halfCone = Math.toRadians(spout.coneDegrees() * HALF);
        Vec3[] basis = basisAcross(spout.axis());
        List<Spark> sparks = new ArrayList<>(LICKS_PER_TICK + EMBERS_PER_TICK);
        for (int index = 0; index < LICKS_PER_TICK; index++) {
            double speed = reachSpeed * between(random, LICK_SLOWEST, 1);
            sparks.add(spark(Kind.LICK, spout, basis, halfCone, speed, random));
        }
        for (int index = 0; index < EMBERS_PER_TICK; index++) {
            double speed = reachSpeed * between(random, EMBER_SLOWEST, EMBER_FASTEST);
            sparks.add(spark(Kind.EMBER, spout, basis, halfCone, speed, random));
        }
        return sparks;
    }

    private static Spark spark(Kind kind, Spout spout, Vec3[] basis, double halfCone, double speed,
                               RandomSource random) {
        Vec3 velocity = withinCone(spout.axis(), basis, halfCone, random).scale(speed);
        return new Spark(kind, spout.apex().add(velocity.scale(random.nextDouble())), velocity);
    }

    /**
     * A unit vector inside the cone, spread evenly over the cone's cap.
     *
     * @param axis     the cone's axis
     * @param basis    two unit vectors across the axis
     * @param halfCone half the cone's angle, in radians
     * @param random   the random source
     * @return the unit vector
     */
    private static Vec3 withinCone(Vec3 axis, Vec3[] basis, double halfCone, RandomSource random) {
        double cosine = 1 - random.nextDouble() * (1 - Math.cos(halfCone));
        double sine = Math.sqrt(Math.max(0, 1 - cosine * cosine));
        double turn = random.nextDouble() * TWO_PI;
        return axis.scale(cosine).add(basis[0].scale(sine * Math.cos(turn))).add(basis[1].scale(sine * Math.sin(turn)));
    }

    private static Vec3[] basisAcross(Vec3 axis) {
        Vec3 seed = Math.abs(axis.y) > NEAR_VERTICAL ? WORLD_EAST : WORLD_UP;
        Vec3 across = axis.cross(seed).normalize();
        return new Vec3[] {across, axis.cross(across).normalize()};
    }

    private static double between(RandomSource random, double low, double high) {
        return low + random.nextDouble() * (high - low);
    }
}
