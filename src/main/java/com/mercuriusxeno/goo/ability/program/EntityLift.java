package com.mercuriusxeno.goo.ability.program;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import java.util.function.IntPredicate;

/**
 * Carries every entity standing in a column of air upward: its rise is
 * raised to the column's speed, eased to land on the column's top, its
 * sideways motion kept and its fall cleared, so an entity rides up to the
 * column's top, drops back in and is carried up again, hovering there; over
 * the column a glide zone slows a fall to its cap, so dropping from a tall
 * column to a short one glides down onto it. A player's own client moves
 * it, so the server only clears a player's fall.
 * updraft-is-typhoons-world-goo-that-lifts-the-entities-above-it
 * updraft-goo-lifts-one-block-per-layer
 * operator ruling 2026-11-17: a tall zone above the push smooths transitions with slow fall and air control
 */
final class EntityLift {

    /** A shaft is one block wide. */
    static final double SHAFT_HALF_WIDTH = 0.5;

    private EntityLift() {
    }

    /**
     * Carries every entity whose feet stand inside the column or the glide
     * zone over it, spectators aside: each one's fall clears, and each but a
     * player, whose own client moves it, rises in the column and falls no
     * faster than the cap over it.
     * operator report 2026-11-17: moving over updraft goo stalled and hitched the player
     *
     * @param level       the level to scan
     * @param base        the middle of the column's floor
     * @param radius      the column's half width in blocks
     * @param height      the column's height in blocks
     * @param glideHeight the glide zone's height over the column's top, in blocks
     * @param speed       the rise the column carries at, in blocks per tick
     * @param fallCap     the fastest a fall in the glide zone runs, in blocks per tick
     */
    static void liftInColumn(ServerLevel level, Vec3 base, double radius, double height, double glideHeight,
                             double speed, double fallCap) {
        double reach = height + glideHeight;
        AABB column = columnBox(base, radius, reach);
        for (Entity entity : level.getEntities((Entity) null, column, entity -> !entity.isSpectator())) {
            if (!inColumn(entity.position(), base, radius, reach)) {
                continue;
            }
            entity.resetFallDistance();
            if (entity instanceof Player) {
                continue;
            }
            Vec3 now = entity.getDeltaMovement();
            Vec3 carried = carried(now, base.y + height - entity.getY(), speed, fallCap);
            if (!carried.equals(now)) {
                entity.setDeltaMovement(carried);
                entity.hurtMarked = true;
            }
        }
    }

    /**
     * A velocity in a column or the glide zone over it: below the column's
     * top it rises as {@link #risen} carries it; over the top its fall runs
     * no faster than the cap and a rise stays, so a drop glides down.
     *
     * @param velocity the velocity now
     * @param toTop    the blocks from the entity's feet up to the column's top, negative over it
     * @param speed    the column's rise, in blocks per tick
     * @param fallCap  the fastest a fall over the column runs, in blocks per tick
     * @return the carried velocity
     */
    static Vec3 carried(Vec3 velocity, double toTop, double speed, double fallCap) {
        if (toTop > 0) {
            return risen(velocity, speed, toTop);
        }
        return new Vec3(velocity.x, Math.max(velocity.y, -fallCap), velocity.z);
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
        BlockPos below = floor.below();
        double sunk = standingDepth(level.getBlockState(below).getCollisionShape(level, below));
        Vec3 base = Vec3.atBottomCenterOf(floor).subtract(0, sunk, 0);
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
     * How far below the shaft's floor block an entity standing on the block
     * under it has its feet: a prism's column stands short of a full block,
     * so a rider on it stands inside the prism's own cell, and the shaft
     * reaches down to that top to carry it.
     * lift-prism-levitates-the-block-above
     *
     * @param belowShape the collision shape of the block under the shaft
     * @return the depth in blocks, zero under a full or open block
     */
    static double standingDepth(VoxelShape belowShape) {
        return belowShape.isEmpty() ? 0 : Math.max(0, 1 - belowShape.max(Direction.Axis.Y));
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
     * A velocity carried up a column: its rise at least the column's speed,
     * cut to what is left of the column so the entity lands on its top
     * rather than flying a block past it, its sideways motion kept; a faster
     * rise is kept.
     * updraft-goo-lifts-one-block-per-layer
     *
     * @param velocity the velocity now
     * @param speed    the column's rise, in blocks per tick
     * @param toTop    the blocks from the entity's feet up to the column's top
     * @return the risen velocity
     */
    static Vec3 risen(Vec3 velocity, double speed, double toTop) {
        return lifted(velocity, Math.min(speed, toTop));
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
