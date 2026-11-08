package com.mercuriusxeno.goo.network;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Where a mob-first stream's gnats converge while its swarm bites no mob:
 * the block under the crosshair, or the point at the stream's reach where
 * the crosshair meets none; and how fast a gnat flies, which the swarm's
 * bite waits on (decision decay-gnats-degrade-each-block-once).
 */
public final class SwarmMotes {

    /** A seeking gnat's speed leaving the glove, blocks per tick. */
    public static final double GNAT_SPEED = 0.35;

    private SwarmMotes() {
    }

    /**
     * The box the gnats converge on this tick.
     *
     * @param crosshair the block the crosshair lands on, or a miss at the stream's reach
     * @return the crosshair block's cell, else the point at the stream's reach
     */
    static AABB moteTarget(BlockHitResult crosshair) {
        if (crosshair.getType() == HitResult.Type.BLOCK) {
            return new AABB(crosshair.getBlockPos());
        }
        return new AABB(crosshair.getLocation(), crosshair.getLocation());
    }
}
