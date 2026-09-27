package com.mercuriusxeno.goo.block.crucible;

import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Steers an item thrown roughly at the crucible's mouth into it (decision
 * rim-and-mouth-items-slide-inward): the item's arc is played forward with the
 * item entity's own gravity and drag, and when it would come down within reach of
 * the mouth, its sideways speed is rewritten so the same arc lands at the mouth's
 * center. Its vertical speed is kept, so the throw keeps its height and timing, and
 * the client, running the same physics, flies the corrected arc on its own.
 */
public final class CrucibleAimAssist {

    /** How far from the mouth's center an arc may come down and still be steered in. */
    static final double AIM_TOLERANCE = 0.5;
    /** The most ticks ahead an arc is played. */
    static final int MAX_FLIGHT_TICKS = 60;
    /** The speed an item entity loses to gravity each tick. */
    static final double GRAVITY = 0.04;
    /** The share of its motion an airborne item entity keeps each tick. */
    static final double DRAG = 0.98;

    /** The mouth's center in block-relative X and Z. */
    private static final double MOUTH_CENTER = (CrucibleBasin.FOOTPRINT_MIN + CrucibleBasin.FOOTPRINT_MAX) / 2.0;

    private CrucibleAimAssist() {}

    /**
     * Where an airborne item's arc comes down through the rim's height, falling.
     *
     * @param position the item's feet relative to the block
     * @param delta    the item's motion
     * @return the point it crosses the rim's height and the ticks it takes, or null
     *         when it never comes down through the rim's height within the look-ahead
     */
    public static @Nullable Landing landing(Vec3 position, Vec3 delta) {
        double x = position.x;
        double y = position.y;
        double z = position.z;
        double vx = delta.x;
        double vy = delta.y;
        double vz = delta.z;
        for (int tick = 1; tick <= MAX_FLIGHT_TICKS; tick++) {
            vy -= GRAVITY;
            double fromY = y;
            x += vx;
            y += vy;
            z += vz;
            vx *= DRAG;
            vy *= DRAG;
            vz *= DRAG;
            if (fromY > CrucibleBasin.RIM_Y && y <= CrucibleBasin.RIM_Y) {
                return new Landing(x, z, tick);
            }
        }
        return null;
    }

    /**
     * The motion that steers an item thrown roughly at the mouth into its center.
     *
     * @param position the item's feet relative to the block
     * @param delta    the item's motion
     * @return the steered motion, or null when the arc comes down out of reach of the mouth
     */
    public static @Nullable Vec3 aimedDelta(Vec3 position, Vec3 delta) {
        Landing landing = landing(position, delta);
        if (landing == null || !withinReach(landing)) { return null; }
        double travel = travelPerUnitSpeed(landing.ticks());
        return new Vec3((MOUTH_CENTER - position.x) / travel, delta.y, (MOUTH_CENTER - position.z) / travel);
    }

    /**
     * @param landing where an arc comes down
     * @return true when it comes down within the aim's tolerance of the mouth's center
     */
    private static boolean withinReach(Landing landing) {
        double dx = landing.x() - MOUTH_CENTER;
        double dz = landing.z() - MOUTH_CENTER;
        return dx * dx + dz * dz <= AIM_TOLERANCE * AIM_TOLERANCE;
    }

    /**
     * @param ticks the ticks flown
     * @return the distance a unit of sideways speed carries an item over that many ticks, under drag
     */
    private static double travelPerUnitSpeed(int ticks) {
        return (1.0 - Math.pow(DRAG, ticks)) / (1.0 - DRAG);
    }

    /**
     * Where an arc comes down through the rim's height.
     *
     * @param x     the X it comes down at, relative to the block
     * @param z     the Z it comes down at, relative to the block
     * @param ticks the ticks it takes to get there
     */
    public record Landing(double x, double z, int ticks) {
    }
}
