package com.mercuriusxeno.goo.ability.deep;

import com.mercuriusxeno.goo.registry.GooAttachments;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * How a Sculker moves. Pressing sneak and jump together enters the dive,
 * and from then on every solid block is swimmable, bedrock included: the
 * body passes through stone with no key pressed into a block, weightless,
 * the movement input driving it along the look, jump lifting it and sneak
 * sinking it. The diver leaves the dive by rising out through a block's top
 * face and jumping clear, which stands it on that block. The world's floor
 * and ceiling hold a diver from leaving the world and never from coming
 * back into it, so a diver below the floor rises back in on jump and no
 * diver is trapped under the ground, creative included.
 * decision sculker-swims-solid-blocks-and-surfaces-at-will
 */
public final class SculkerSwim {

    /** The speed the swim drives toward under full input, in blocks per tick. */
    static final double SWIM_SPEED = 0.6;
    /** The share of the way the swim turns toward the driven speed each tick. */
    static final double SWIM_STEER = 0.25;
    /** How far the box shrinks before asking whether it is inside a solid block, so a body resting on a face is not. */
    private static final double INSIDE_INSET = 1.0E-7;
    /** How far below the feet a diver looks for the top face it has just risen out of. */
    private static final double SURFACE_REACH = 0.5;
    private static final double SINK = -1;
    private static final double LIFT = 1;
    private static final double HOLD = 0;

    private SculkerSwim() {
    }

    /**
     * Whether a player's Sculker stands now.
     *
     * @param player the player
     * @return true while the brew stands
     */
    public static boolean dives(Player player) {
        return player.getData(GooAttachments.SCULKER).standsAt(player.level().getGameTime());
    }

    /**
     * Whether a player passes through solid blocks this tick.
     *
     * @param player the player
     * @return true while its Sculker stands and it is in the dive
     */
    public static boolean passesThrough(Player player) {
        return dives(player) && player.getData(GooAttachments.SCULKER).diving();
    }

    /**
     * Moves a server player into or out of the dive by its last input: sneak
     * and jump together take it in while the brew stands, and rising out of
     * a top face with jump held takes it out. The state syncs to its client.
     *
     * @param player the player
     */
    public static void updateDive(ServerPlayer player) {
        Sculker sculker = player.getData(GooAttachments.SCULKER);
        if (!sculker.diving() && !dives(player)) {
            return;
        }
        boolean diving = nextDiving(sculker.diving() && dives(player), dives(player), player.getLastClientInput(),
                insideSolid(player), overATopFace(player));
        if (diving != sculker.diving()) {
            player.setData(GooAttachments.SCULKER, sculker.withDiving(diving));
        }
    }

    /**
     * Whether a player is in the dive next tick.
     *
     * @param diving      whether it is in the dive now
     * @param stands      whether its brew stands
     * @param keys        its held keys
     * @param inside      whether its body is inside a solid block
     * @param overATopFace whether a block's top face lies just under its feet
     * @return true where it stays in or enters the dive
     */
    static boolean nextDiving(boolean diving, boolean stands, Input keys, boolean inside, boolean overATopFace) {
        if (!stands) {
            return false;
        }
        if (!diving) {
            return keys.shift() && keys.jump();
        }
        return !jumpsClear(keys, inside, overATopFace);
    }

    private static boolean jumpsClear(Input keys, boolean inside, boolean overATopFace) {
        return keys.jump() && !keys.shift() && !inside && overATopFace;
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

    private static boolean overATopFace(Player player) {
        AABB under = player.getBoundingBox().deflate(INSIDE_INSET).move(0, -SURFACE_REACH, 0);
        return player.level().getBlockCollisions(null, under).iterator().hasNext();
    }

    /**
     * The rise a diver's held keys give its swim: sneak sinks it, winning
     * over jump, so holding both sinks a player into the ground; jump alone
     * lifts it.
     *
     * @param keys the held keys
     * @return -1 to sink, 1 to rise, 0 to hold its depth
     */
    public static double riseOf(Input keys) {
        if (keys.shift()) {
            return SINK;
        }
        return keys.jump() ? LIFT : HOLD;
    }

    /**
     * One tick of a diver's swim: the velocity it moves at next, with its
     * rise or fall stopped where it would carry the body out past the world's
     * floor or ceiling, never where it brings the body back in.
     *
     * @param player  the diver
     * @param forward the forward input, -1 to 1
     * @param strafe  the strafe input, -1 to 1, left positive
     * @param rise    the rise input, jump 1, sneak -1
     * @return the velocity the swim moves the diver at
     */
    public static Vec3 swimStroke(Player player, double forward, double strafe, double rise) {
        Vec3 swim = swum(player.getDeltaMovement(), forward, strafe, rise, player.getLookAngle());
        AABB body = player.getBoundingBox();
        Level level = player.level();
        return new Vec3(swim.x, heldInTheWorld(swim.y, body.minY, body.maxY, level.getMinY(), level.getMaxY() + 1),
                swim.z);
    }

    /**
     * A vertical move kept from carrying a body out of the world: a fall
     * stops at the floor and a rise at the ceiling, and a body already past
     * either moves back in freely and no further out.
     *
     * @param move    the vertical move
     * @param bottom  the body's bottom
     * @param top     the body's top
     * @param floor   the world's floor
     * @param ceiling the world's ceiling
     * @return the move the world allows
     */
    static double heldInTheWorld(double move, double bottom, double top, double floor, double ceiling) {
        if (move < 0) {
            return bottom + move >= floor ? move : Math.min(0, floor - bottom);
        }
        return top + move <= ceiling ? move : Math.max(0, ceiling - top);
    }

    /**
     * The velocity a swim carries into the next tick: the input, turned to
     * the look, pitch included, with jump lifting and sneak sinking, steered
     * toward from the velocity before.
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
