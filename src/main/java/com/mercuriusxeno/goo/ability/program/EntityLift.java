package com.mercuriusxeno.goo.ability.program;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Carries every entity standing in a column of air upward: its rise is
 * raised to the column's speed, its sideways motion kept, and its fall
 * cleared, so an entity rides up to the column's top, drops back in and is
 * carried up again, hovering there.
 * updraft-blob-stands-a-column-of-wind
 */
final class EntityLift {

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
