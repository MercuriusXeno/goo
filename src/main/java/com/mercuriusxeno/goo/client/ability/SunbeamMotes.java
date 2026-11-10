package com.mercuriusxeno.goo.client.ability;

import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;

/**
 * Where and how the light motes a Sunbeam ray sheds each client tick leave
 * it: at random points along each drawn segment, inside the beam, rising
 * slowly with a little sideways drift. The rate runs per block of beam and
 * stays low, since the end_rod sparks these replace were too many.
 * decision sunbeam-sheds-motes-rising-from-the-beam
 */
final class SunbeamMotes {

    /** Motes shed per block of beam each client tick, on average. */
    static final double MOTES_PER_BLOCK = 0.08;
    /** The slowest and the fastest a mote rises, in blocks per tick. */
    private static final double SLOWEST_RISE = 0.012;
    private static final double RISE_VARIANCE = 0.018;
    /** The most a mote drifts sideways, as a share of its rise. */
    private static final double MOST_DRIFT_OF_RISE = 0.5;
    private static final double SHORTEST_SEGMENT = 1.0e-3;
    private static final double TWO_PI = 2 * Math.PI;
    private static final Vec3 UP = new Vec3(0, 1, 0);
    private static final Vec3 EAST = new Vec3(1, 0, 0);
    /** An axis closer to vertical than this takes east as its reference for the cross-section. */
    private static final double NEARLY_VERTICAL = 0.99;

    private SunbeamMotes() {
    }

    /**
     * The motes one client tick sheds along every segment of a ray.
     *
     * @param segments each straight stretch the ray draws, world space
     * @param radius   the beam's drawn radius, in blocks
     * @param random   the random source
     * @return each mote's spawn point and velocity
     */
    static List<Mote> shedAlong(List<Segment> segments, double radius, RandomSource random) {
        List<Mote> motes = new ArrayList<>();
        for (Segment segment : segments) {
            Vec3 along = segment.to().subtract(segment.from());
            double length = along.length();
            if (length < SHORTEST_SEGMENT) {
                continue;
            }
            int count = countFor(length, random);
            for (int mote = 0; mote < count; mote++) {
                motes.add(new Mote(spawnPoint(segment.from(), along, radius, random), riseVelocity(random)));
            }
        }
        return motes;
    }

    /**
     * How many motes a segment sheds this tick: its length times the rate,
     * the fraction left over shed by chance.
     *
     * @param length the segment's length, in blocks
     * @param random the random source
     * @return the motes to shed
     */
    private static int countFor(double length, RandomSource random) {
        double expected = length * MOTES_PER_BLOCK;
        int whole = (int) expected;
        return random.nextDouble() < expected - whole ? whole + 1 : whole;
    }

    /**
     * A random point inside the beam: somewhere along the segment, and
     * somewhere across the disc of the beam's radius around its axis.
     *
     * @param from   the segment's start
     * @param along  the segment's start to its end
     * @param radius the beam's drawn radius
     * @param random the random source
     * @return the spawn point
     */
    private static Vec3 spawnPoint(Vec3 from, Vec3 along, double radius, RandomSource random) {
        Vec3 axis = along.normalize();
        Vec3 reference = Math.abs(axis.dot(UP)) > NEARLY_VERTICAL ? EAST : UP;
        Vec3 across = axis.cross(reference).normalize();
        Vec3 acrossToo = axis.cross(across);
        double angle = random.nextDouble() * TWO_PI;
        double distance = radius * Math.sqrt(random.nextDouble());
        return from.add(along.scale(random.nextDouble()))
                .add(across.scale(Math.cos(angle) * distance))
                .add(acrossToo.scale(Math.sin(angle) * distance));
    }

    /**
     * A gentle rise with a sideways drift under half of it.
     *
     * @param random the random source
     * @return the velocity, in blocks per tick
     */
    private static Vec3 riseVelocity(RandomSource random) {
        double rise = SLOWEST_RISE + random.nextDouble() * RISE_VARIANCE;
        double drift = rise * MOST_DRIFT_OF_RISE * random.nextDouble();
        double heading = random.nextDouble() * TWO_PI;
        return new Vec3(Math.cos(heading) * drift, rise, Math.sin(heading) * drift);
    }

    /**
     * One straight stretch of a drawn ray.
     *
     * @param from where it starts, world space
     * @param to   where it ends, world space
     */
    record Segment(Vec3 from, Vec3 to) {
    }

    /**
     * One light mote to spawn.
     *
     * @param at       its spawn point, world space
     * @param velocity its spawn velocity, in blocks per tick
     */
    record Mote(Vec3 at, Vec3 velocity) {
    }
}
