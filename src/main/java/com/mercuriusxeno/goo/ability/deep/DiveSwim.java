package com.mercuriusxeno.goo.ability.deep;

import com.mercuriusxeno.goo.registry.GooAttachments;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * How a diving player moves: while the dive stands it passes through solid
 * blocks wherever its body is already inside one, wherever it sinks into the
 * ground with sneak, and wherever it walks into a wall, so it walks open
 * ground as ever and enters stone at will; once its body stands clear in the
 * open, the blocks hold it again. Inside solid blocks it swims weightless, the
 * way it swims in water: the movement input drives it along the look, jump
 * lifts it and sneak sinks it, and nothing in the stone suffocates it.
 * decision dive-is-a-brew-of-warden-senses-and-free-swimming-through-solid-blocks
 */
public final class DiveSwim {

    /** The speed the swim drives toward under full input, in blocks per tick. */
    static final double SWIM_SPEED = 0.3;
    /** The share of the way the swim turns toward the driven speed each tick. */
    static final double SWIM_STEER = 0.25;
    /** How far the box shrinks before asking whether it is inside a solid block, so a body brushing a wall is not. */
    private static final double INSIDE_INSET = 0.05;

    private DiveSwim() {
    }

    /**
     * Whether a player's dive stands now.
     *
     * @param player the player
     * @return true while the dive stands
     */
    public static boolean dives(Player player) {
        return player.getData(GooAttachments.DIVE).standsAt(player.level().getGameTime());
    }

    /**
     * Whether a player passes through solid blocks this tick.
     *
     * @param player the player
     * @return true while its dive stands and it is inside, sinking into or pushing into solid blocks
     */
    public static boolean passesThrough(Player player) {
        return dives(player) && entersStone(insideSolid(player), player.isShiftKeyDown(), player.horizontalCollision);
    }

    /**
     * Whether a diver enters or stays in stone.
     *
     * @param inside   whether its body is inside a solid block now
     * @param sinking  whether it holds sneak, sinking into the ground below
     * @param pushing  whether it walked into a wall last tick
     * @return true where the blocks let it through
     */
    static boolean entersStone(boolean inside, boolean sinking, boolean pushing) {
        return inside || sinking || pushing;
    }

    /**
     * Whether a player's body is inside a solid block.
     *
     * @param player the player
     * @return true where any block its body overlaps has a collision shape it overlaps
     */
    public static boolean insideSolid(Player player) {
        AABB body = player.getBoundingBox().deflate(INSIDE_INSET);
        return player.level().getBlockCollisions(null, body).iterator().hasNext();
    }

    /**
     * The velocity a swim inside stone carries into the next tick: the
     * input, turned to the look, pitch included, with jump lifting and sneak
     * sinking, steered toward from the velocity before.
     *
     * @param velocity the velocity before
     * @param forward  the forward input, -1 to 1
     * @param strafe   the strafe input, -1 to 1, left positive
     * @param rise     the rise input, jump 1, sneak -1
     * @param look     the look direction, unit length
     * @return the swim's velocity
     */
    public static Vec3 swum(Vec3 velocity, double forward, double strafe, double rise, Vec3 look) {
        Vec3 flat = new Vec3(look.x, 0, look.z);
        Vec3 left = flat.lengthSqr() == 0 ? Vec3.ZERO : new Vec3(look.z, 0, -look.x).normalize();
        Vec3 driven = look.scale(forward).add(left.scale(strafe)).add(0, rise, 0);
        Vec3 target = driven.lengthSqr() > 1 ? driven.normalize().scale(SWIM_SPEED) : driven.scale(SWIM_SPEED);
        return velocity.add(target.subtract(velocity).scale(SWIM_STEER));
    }
}
