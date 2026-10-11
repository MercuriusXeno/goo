package com.mercuriusxeno.goo.ability.program;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.function.IntPredicate;

/**
 * Carries every entity standing in a column of air upward: its rise is
 * raised to the column's speed, its sideways motion kept, and its fall
 * cleared, so an entity rides up to the column's top, drops back in and is
 * carried up again, hovering there.
 * updraft-blob-stands-a-column-of-wind
 */
final class EntityLift {

    /** A shaft is one block wide. */
    static final double SHAFT_HALF_WIDTH = 0.5;

    private EntityLift() {
    }

    /**
     * Lifts every entity whose feet stand inside the column, spectators aside.
     *
     * @param level  the level to scan
     * @param base   the middle of the column's floor
     * @param radius the column's half width in blocks
     * @param height the column's height in blocks
     * @param speed  the rise the column carries at, in blocks per tick
     */
    static void liftInColumn(ServerLevel level, Vec3 base, double radius, double height, double speed) {
        AABB column = columnBox(base, radius, height);
        for (Entity entity : level.getEntities((Entity) null, column, entity -> !entity.isSpectator())) {
            if (inColumn(entity.position(), base, radius, height)) {
                entity.setDeltaMovement(lifted(entity.getDeltaMovement(), speed));
                entity.resetFallDistance();
                entity.hurtMarked = true;
            }
        }
    }

    /**
     * Rides every entity whose feet stand in the one-block shaft rising from
     * a floor block up to the first block that stops movement: each rises at
     * the shaft's pace, or sinks gently while sneaking, its fall cleared
     * either way; stepping sideways out of the shaft steps off it.
     * lift-prism-levitates-the-block-above
     *
     * @param level the level to scan
     * @param floor the shaft's lowest block
     * @param cap   the tallest the shaft runs, in blocks
     * @param rise  the rise it carries at, in blocks per tick
     * @param sink  the pace a sneaking rider sinks at, in blocks per tick
     */
    static void rideShaft(ServerLevel level, BlockPos floor, int cap, double rise, double sink) {
        int height = shaftHeight(level, floor, cap);
        Vec3 base = Vec3.atBottomCenterOf(floor);
        AABB shaft = columnBox(base, SHAFT_HALF_WIDTH, height);
        for (Entity entity : level.getEntities((Entity) null, shaft, entity -> !entity.isSpectator())) {
            if (inColumn(entity.position(), base, SHAFT_HALF_WIDTH, height)) {
                entity.setDeltaMovement(ridden(entity.getDeltaMovement(), entity.isShiftKeyDown(), rise, sink));
                entity.resetFallDistance();
                entity.hurtMarked = true;
            }
        }
    }

    /**
     * A shaft's height in a world: the open blocks from its floor up before
     * the first whose collision shape stops movement, at most the cap.
     *
     * @param level the world
     * @param floor the shaft's lowest block
     * @param cap   the tallest the shaft runs
     * @return the shaft's height in blocks
     */
    static int shaftHeight(BlockGetter level, BlockPos floor, int cap) {
        return shaftHeight(up -> level.getBlockState(floor.above(up)).getCollisionShape(level, floor.above(up))
                .isEmpty(), cap);
    }

    /**
     * How many open blocks stand in a row from a shaft's floor up, the floor
     * included, before the first that stops movement, at most the cap.
     *
     * @param opensAt whether the block that many above the floor lets an entity through
     * @param cap     the tallest the shaft runs
     * @return the shaft's height in blocks
     */
    static int shaftHeight(IntPredicate opensAt, int cap) {
        int height = 0;
        while (height < cap && opensAt.test(height)) {
            height++;
        }
        return height;
    }

    /**
     * A rider's velocity in a shaft: rising at the shaft's pace, or sinking
     * gently while sneaking, its sideways motion kept.
     *
     * @param velocity the velocity now
     * @param sneaking whether the rider sneaks
     * @param rise     the shaft's rise
     * @param sink     the sneaking rider's sink
     * @return the ridden velocity
     */
    static Vec3 ridden(Vec3 velocity, boolean sneaking, double rise, double sink) {
        return sneaking ? new Vec3(velocity.x, -sink, velocity.z) : lifted(velocity, rise);
    }

    /**
     * The box a column fills.
     *
     * @param base   the middle of the column's floor
     * @param radius the column's half width
     * @param height the column's height
     * @return the box
     */
    static AABB columnBox(Vec3 base, double radius, double height) {
        return new AABB(base.x - radius, base.y, base.z - radius, base.x + radius, base.y + height, base.z + radius);
    }

    /**
     * Whether a point stands inside the column: within its half width of the
     * middle on both level axes, from its floor up to below its top.
     *
     * @param point  the point, an entity's feet
     * @param base   the middle of the column's floor
     * @param radius the column's half width
     * @param height the column's height
     * @return true inside the column
     */
    static boolean inColumn(Vec3 point, Vec3 base, double radius, double height) {
        return Math.abs(point.x - base.x) <= radius && Math.abs(point.z - base.z) <= radius
                && point.y >= base.y && point.y < base.y + height;
    }

    /**
     * A velocity carried upward: its rise at least the column's speed, its
     * sideways motion kept.
     *
     * @param velocity the velocity now
     * @param speed    the column's rise
     * @return the lifted velocity
     */
    static Vec3 lifted(Vec3 velocity, double speed) {
        return new Vec3(velocity.x, Math.max(velocity.y, speed), velocity.z);
    }
}
