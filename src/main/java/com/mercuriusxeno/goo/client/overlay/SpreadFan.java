package com.mercuriusxeno.goo.client.overlay;

import com.mercuriusxeno.goo.ability.Charge;
import com.mercuriusxeno.goo.ability.crystal.KnifeRain;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;

/**
 * Knives' spread fan as points: the cone the release slings at the current
 * charge, symmetric about the look, its two edges and center each tracing
 * the flight a knife at that yaw takes from the hand, and ribs across the
 * cone joining the edges so it reads as a fan.
 * knives-aims-a-spread-fan-not-a-blob-line
 */
final class SpreadFan {

    /** The ribs joining the edges, the last closing the fan at its far end. */
    static final int RIBS = 3;
    /** Points along each rib, edge to edge. */
    private static final int RIB_SAMPLES = 9;
    private static final double HALF = 0.5;

    private SpreadFan() {
    }

    /**
     * Where the fan stops: the height a falling knife lands at and the
     * distance across the ground it reaches.
     *
     * @param floorY     the height a falling knife ends at
     * @param horizontal the blocks across the ground from the hand a knife ends at
     */
    record FanReach(double floorY, double horizontal) {
    }

    /**
     * The fan's lines, each a polyline from the hand.
     *
     * @param leftEdge  the left edge's flight
     * @param center    the center's flight
     * @param rightEdge the right edge's flight
     * @param ribs      the ribs across the cone, each from the left edge to the right
     * @param leftYaw   the left edge's yaw, in degrees
     * @param rightYaw  the right edge's yaw, in degrees
     */
    record Fan(List<Vec3> leftEdge, List<Vec3> center, List<Vec3> rightEdge, List<List<Vec3>> ribs,
               double leftYaw, double rightYaw) {
    }

    /**
     * The fan a release at a share of the charge slings across.
     *
     * @param hand   the point the knives leave from
     * @param yaw    the thrower's yaw, in degrees
     * @param pitch  the thrower's pitch, in degrees
     * @param charge the delivery's charge
     * @param share  the share of a full charge the hold reached, 0 to 1
     * @param reach  where the fan stops
     * @return the fan
     */
    static Fan of(Vec3 hand, float yaw, float pitch, Charge charge, float share, FanReach reach) {
        double half = charge.spreadDegrees(share) * HALF;
        int ticks = flightTicks(hand, KnifeRain.aimedVelocity(yaw, pitch, 0), reach);
        List<List<Vec3>> ribs = new ArrayList<>(RIBS);
        for (int rib = 1; rib <= RIBS; rib++) {
            ribs.add(rib(hand, yaw, pitch, half, Math.max(1, Math.round((float) ticks * rib / RIBS))));
        }
        return new Fan(path(hand, KnifeRain.aimedVelocity(yaw, pitch, -half), ticks),
                path(hand, KnifeRain.aimedVelocity(yaw, pitch, 0), ticks),
                path(hand, KnifeRain.aimedVelocity(yaw, pitch, half), ticks), ribs, yaw - half, yaw + half);
    }

    /**
     * The ticks a knife flies before the fan stops it: until it falls to the
     * floor or crosses the reach, at most a knife's whole flight.
     *
     * @param hand     where it leaves
     * @param velocity its velocity leaving the hand
     * @param reach    where the fan stops
     * @return the ticks, at least 1
     */
    static int flightTicks(Vec3 hand, Vec3 velocity, FanReach reach) {
        Vec3 position = hand;
        Vec3 moving = velocity;
        for (int tick = 1; tick < KnifeRain.MOST_FLIGHT_TICKS; tick++) {
            position = position.add(moving);
            boolean landed = moving.y < 0 && position.y <= reach.floorY();
            moving = KnifeRain.nextVelocity(moving);
            if (landed || Math.hypot(position.x - hand.x, position.z - hand.z) >= reach.horizontal()) {
                return tick;
            }
        }
        return KnifeRain.MOST_FLIGHT_TICKS;
    }

    /**
     * A knife's flight from the hand, one point a tick.
     *
     * @param hand     where it leaves
     * @param velocity its velocity leaving the hand
     * @param ticks    the ticks it flies
     * @return the points, ticks + 1 of them
     */
    private static List<Vec3> path(Vec3 hand, Vec3 velocity, int ticks) {
        List<Vec3> points = new ArrayList<>(ticks + 1);
        Vec3 position = hand;
        Vec3 moving = velocity;
        points.add(position);
        for (int tick = 0; tick < ticks; tick++) {
            position = position.add(moving);
            moving = KnifeRain.nextVelocity(moving);
            points.add(position);
        }
        return points;
    }

    /**
     * Where knives across the cone are a number of ticks into their flight,
     * from the left edge to the right.
     *
     * @param hand  where they leave
     * @param yaw   the thrower's yaw, in degrees
     * @param pitch the thrower's pitch, in degrees
     * @param half  half the cone, in degrees
     * @param ticks the ticks into the flight
     * @return the rib's points
     */
    private static List<Vec3> rib(Vec3 hand, float yaw, float pitch, double half, int ticks) {
        List<Vec3> points = new ArrayList<>(RIB_SAMPLES);
        for (int sample = 0; sample < RIB_SAMPLES; sample++) {
            double offset = Mth.lerp((double) sample / (RIB_SAMPLES - 1), -half, half);
            points.add(KnifeRain.positionAt(hand, KnifeRain.aimedVelocity(yaw, pitch, offset), ticks));
        }
        return points;
    }
}
