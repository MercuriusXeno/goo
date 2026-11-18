package com.mercuriusxeno.goo.client.particle;

import net.minecraft.world.phys.Vec3;

/**
 * How a loose void mote flies into Vacuum's blob: fast and straight, at one
 * pace from wherever in the cone it starts, each tick covering an even share
 * of what lies between it and where the blob is now, so it lands on the blob
 * as its flight ends even while the caster moves.
 * vacuum-motes-fly-loose-from-the-cone-to-the-blob
 */
final class MoteHoming {

    /** The most ticks a mote flies, from the cone's far end. */
    static final int MOTE_FLIGHT_TICKS = 9;
    /** Blocks a mote covers each tick: the cone's 9-block reach inside its flight. */
    private static final double MOTE_SPEED = 1.0;

    private MoteHoming() {
    }

    /**
     * One tick's move toward the blob.
     *
     * @param at        where the mote is
     * @param blob      where the blob is this tick
     * @param ticksLeft the ticks of flight the mote has left, this one counted
     * @return the move, in blocks
     */
    static Vec3 stepToward(Vec3 at, Vec3 blob, int ticksLeft) {
        return blob.subtract(at).scale(1.0 / Math.max(1, ticksLeft));
    }

    /**
     * The ticks a mote takes to fly from where it starts to the blob at
     * MOTE_SPEED, so a mote near the blob lands sooner rather than crawling.
     *
     * @param from where the mote starts
     * @param to   where the blob stands
     * @return the flight, 1 to MOTE_FLIGHT_TICKS ticks
     */
    static int flightTicks(Vec3 from, Vec3 to) {
        return (int) Math.clamp(Math.ceil(from.distanceTo(to) / MOTE_SPEED), 1, MOTE_FLIGHT_TICKS);
    }
}
