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

    /** How hard its target pulls a gnat each tick, blocks per tick per tick. */
    public static final double PULL = 0.12;
    /**
     * The share of its velocity a gnat keeps each tick. With the pull, it
     * turns a gnat fanned 45 degrees down off the aim onto a floor block
     * before it strikes the floor more than a block short.
     */
    public static final double DRAG = 0.72;
    /**
     * A gnat's speed, blocks per tick: the steady speed the pull and the drag
     * settle on, which it leaves the glove at and the bite waits on.
     */
    public static final double GNAT_SPEED = PULL * DRAG / (1 - DRAG);

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
