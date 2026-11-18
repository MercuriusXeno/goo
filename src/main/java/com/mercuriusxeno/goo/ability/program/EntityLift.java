package com.mercuriusxeno.goo.ability.program;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Carries every entity standing in a column of air upward: its rise is
 * raised to the column's speed, eased to land on the column's top, its
 * sideways motion kept and its fall cleared, so an entity rides up to the
 * column's top, drops back in and is carried up again, hovering there; over
 * the column a glide zone slows a fall to its cap, so dropping from a tall
 * column to a short one glides down onto it. A player's own client moves
 * it, so the server only clears a player's fall. Every living entity in
 * either zone regains air, a player included, faster than airless lunar
 * drains it.
 * updraft-is-typhoons-world-goo-that-lifts-the-entities-above-it
 * updraft-goo-lifts-one-block-per-layer
 * updraft-refills-the-air-of-entities-it-propels
 * operator ruling 2026-11-17: a tall zone above the push smooths transitions with slow fall and air control
 */
final class EntityLift {

    private EntityLift() {
    }

    /**
     * Carries every entity whose feet stand inside the column or the glide
     * zone over it, spectators aside: each one's fall clears, each living
     * one regains air, and each but a player, whose own client moves it,
     * rises in the column and falls no faster than the cap over it.
     * operator report 2026-11-17: moving over updraft goo stalled and hitched the player
     *
     * @param level       the level to scan
     * @param base        the middle of the column's floor
     * @param radius      the column's half width in blocks
     * @param height      the column's height in blocks
     * @param glideHeight the glide zone's height over the column's top, in blocks
     * @param speed       the rise the column carries at, in blocks per tick
     * @param fallCap     the fastest a fall in the glide zone runs, in blocks per tick
     * @param airRefill   the air each living entity in either zone regains a tick
     */
    static void liftInColumn(ServerLevel level, Vec3 base, double radius, double height, double glideHeight,
                             double speed, double fallCap, int airRefill) {
        double reach = height + glideHeight;
        AABB column = columnBox(base, radius, reach);
        for (Entity entity : level.getEntities((Entity) null, column, entity -> !entity.isSpectator())) {
            if (!inColumn(entity.position(), base, radius, reach)) {
                continue;
            }
            entity.resetFallDistance();
            if (entity instanceof LivingEntity living) {
                living.setAirSupply(refilled(living.getAirSupply(), living.getMaxAirSupply(), airRefill));
            }
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
     * An air supply refilled by a column: raised by the refill, at most the
     * entity's full air, never lowered.
     *
     * @param supply the air now
     * @param full   the entity's full air
     * @param refill the air the column refills a tick
     * @return the refilled air
     */
    static int refilled(int supply, int full, int refill) {
        return Math.max(supply, Math.min(full, supply + refill));
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
