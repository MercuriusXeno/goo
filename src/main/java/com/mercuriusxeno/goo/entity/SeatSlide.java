package com.mercuriusxeno.goo.entity;

import net.minecraft.world.phys.Vec3;

/**
 * How a let-go chunk come to rest moves on to its seat in the grid column
 * it stands over, a step each tick so it draws moving rather than jumping
 * there: up first to a seat above it, then across, under half a block, onto
 * the column's middle, then falling into it, never past it.
 * decision terrakinesis-release-drops-the-hunk-and-places-it-on-the-grid
 */
final class SeatSlide {

    /** How far it slides across toward its seat each tick, in blocks. */
    static final double SLIDE_STEP = 0.15;
    /** How far it rises toward a seat above it each tick, in blocks. */
    static final double RISE_STEP = 0.3;
    /** How near counts as there, in blocks. */
    private static final double THERE = 1e-6;

    private SeatSlide() {
    }

    /**
     * Where the chunk stands a tick on toward its seat.
     *
     * @param from      where it stands
     * @param seat      the seat it moves to
     * @param fallSpeed how far it falls this tick once over the seat, in blocks
     * @return where it moves to, before the world's collisions
     */
    static Vec3 step(Vec3 from, Vec3 seat, double fallSpeed) {
        if (seat.y > from.y + THERE) {
            return new Vec3(from.x, Math.min(seat.y, from.y + RISE_STEP), from.z);
        }
        Vec3 across = new Vec3(seat.x - from.x, 0, seat.z - from.z);
        double off = across.length();
        if (off > THERE) {
            return off <= SLIDE_STEP ? new Vec3(seat.x, from.y, seat.z) : from.add(across.scale(SLIDE_STEP / off));
        }
        return new Vec3(seat.x, Math.max(seat.y, from.y - fallSpeed), seat.z);
    }
}
