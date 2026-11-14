package com.mercuriusxeno.goo.client.overlay;

import com.mercuriusxeno.goo.ability.Charge;
import com.mercuriusxeno.goo.ability.crystal.KnifeRain;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Knives' aim as points: one line per knife the current charge slings,
 * scattered across the charged cone the way the release scatters them, each
 * rooted at the blob floating ahead of the hand, where the server launches
 * its knife, and following that knife's flight until it would strike. The
 * scatter is the press's own seed, rolled anew each press and sent with the
 * release, so the lines hold still through a press and the knives fly them.
 * knives-aims-a-spread-fan-not-a-blob-line
 * knives-blob-floats-ahead-of-the-hand-and-spews-the-knives
 */
final class KnifeAimLines {

    private KnifeAimLines() {
    }

    /**
     * Where a knife's path through one tick meets the world.
     */
    @FunctionalInterface
    interface Strike {
        /**
         * The point a path from one point to the next strikes, if any.
         *
         * @param from where the knife is
         * @param to   where it is a tick on
         * @return the point it strikes, or null for open air
         */
        @Nullable Vec3 hit(Vec3 from, Vec3 to);
    }

    /**
     * A held press as the lines read it.
     *
     * @param eye        the thrower's eye
     * @param yaw        the thrower's yaw, in degrees
     * @param pitch      the thrower's pitch, in degrees
     * @param gloveRight whether the glove is in the right hand
     * @param heldTicks  the ticks the press has held the charge
     * @param share      the share of a full charge the hold has reached, 0 to 1
     * @param seed       the press's scatter seed, which the release sends
     */
    record Press(Vec3 eye, float yaw, float pitch, boolean gloveRight, int heldTicks, float share, long seed) {
    }

    /**
     * Every line a held press aims, one per knife, each rooted where the
     * knives leave the floating blob.
     *
     * @param press  the held press
     * @param charge the delivery's charge
     * @param strike where a tick's path meets the world
     * @return the lines, each one point a tick, the first the root
     */
    static List<List<Vec3>> linesOf(Press press, Charge charge, Strike strike) {
        Vec3 root = KnifeRain.leavesFrom(press.eye(), press.yaw(), Vec3.directionFromRotation(press.pitch(),
                press.yaw()), press.gloveRight(), press.heldTicks(), 0);
        return velocitiesAt(press.yaw(), press.pitch(), charge, press.share(), press.seed()).stream()
                .map(velocity -> flight(root, velocity, strike)).toList();
    }

    /**
     * Each line's velocity leaving the blob at a share of the charge: one
     * per knife the release would sling, across the cone it would sling them.
     *
     * @param yaw    the thrower's yaw, in degrees
     * @param pitch  the thrower's pitch, in degrees
     * @param charge the delivery's charge
     * @param share  the share of a full charge the hold reached, 0 to 1
     * @param seed   the press's scatter seed, which the release sends
     * @return a velocity per line, in blocks a tick
     */
    static List<Vec3> velocitiesAt(float yaw, float pitch, Charge charge, float share, long seed) {
        return KnifeRain.releaseVelocities(yaw, pitch, charge, share, RandomSource.create(seed));
    }

    /**
     * A knife's flight from where it leaves, one point a tick, ending where
     * it strikes or once its flight is spent.
     *
     * @param start    where it leaves
     * @param velocity its velocity leaving
     * @param strike   where a tick's path meets the world
     * @return the points, the first where it leaves
     */
    static List<Vec3> flight(Vec3 start, Vec3 velocity, Strike strike) {
        List<Vec3> points = new ArrayList<>();
        points.add(start);
        Vec3 position = start;
        Vec3 moving = velocity;
        for (int tick = 0; tick < KnifeRain.MOST_FLIGHT_TICKS; tick++) {
            Vec3 next = position.add(moving);
            Vec3 struck = strike.hit(position, next);
            if (struck != null) {
                points.add(struck);
                return points;
            }
            points.add(next);
            position = next;
            moving = KnifeRain.nextVelocity(moving);
        }
        return points;
    }
}
