package com.mercuriusxeno.goo.network;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Where a mob-first stream's gnats converge while its swarm bites no mob:
 * the block under the crosshair, or a block-wide cloud at the stream's reach where
 * the crosshair meets none; and how fast a gnat flies, which the swarm's
 * bite waits on (decision decay-gnats-degrade-each-block-once).
 */
public final class SwarmMotes {

    /**
     * A gnat's speed, blocks per tick: past twice a sprint-jumping player's,
     * so the swarm leaves its caster behind; it leaves the glove at it, the
     * pull and the drag settle on it, and the bite waits on it.
     * glove-stream-motes-carry-the-players-travel-velocity
     */
    public static final double GNAT_SPEED = 0.75;
    /**
     * The share of its velocity a gnat keeps each tick. With the pull, it
     * turns a gnat fanned 45 degrees down off the aim onto a floor block
     * before it strikes the floor more than a block short.
     */
    public static final double DRAG = 0.5;
    /** How hard its target pulls a gnat each tick, blocks per tick per tick: what settles it on its speed. */
    public static final double PULL = GNAT_SPEED * (1 - DRAG) / DRAG;
    /** Half the width of the cloud a swarm with no block under the crosshair spreads through at its reach. */
    public static final double CLOUD_HALF_WIDTH = 0.5;
    /** How far off the struck face the gnats converge, so they settle on it rather than inside the block. */
    public static final double FACE_LIFT = 0.05;

    private SwarmMotes() {
    }

    /**
     * The box the gnats converge on this tick: the struck face of the block
     * under the crosshair, lifted just off it. A point inside the block would
     * draw a gnat on a line that strikes the face short of the block.
     *
     * @param crosshair the block the crosshair lands on, or a miss at the stream's reach
     * @return a thin slab over the struck face, else a block-wide cloud around the stream's reach
     */
    static AABB moteTarget(BlockHitResult crosshair) {
        if (crosshair.getType() != HitResult.Type.BLOCK) {
            // operator ruling 2026-11-13: one point ended every swept burst's gnats in a straight line in midair
            return new AABB(crosshair.getLocation(), crosshair.getLocation()).inflate(CLOUD_HALF_WIDTH);
        }
        AABB cell = new AABB(crosshair.getBlockPos());
        Direction face = crosshair.getDirection();
        Direction.Axis axis = face.getAxis();
        double plane = face.getAxisDirection() == Direction.AxisDirection.POSITIVE ? cell.max(axis) : cell.min(axis);
        double lifted = plane + face.getAxisDirection().getStep() * FACE_LIFT;
        double low = Math.min(plane, lifted);
        double high = Math.max(plane, lifted);
        return switch (axis) {
            case X -> new AABB(low, cell.minY, cell.minZ, high, cell.maxY, cell.maxZ);
            case Y -> new AABB(cell.minX, low, cell.minZ, cell.maxX, high, cell.maxZ);
            case Z -> new AABB(cell.minX, cell.minY, low, cell.maxX, cell.maxY, high);
        };
    }
}
