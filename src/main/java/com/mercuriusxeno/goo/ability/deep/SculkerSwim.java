package com.mercuriusxeno.goo.ability.deep;

import com.mercuriusxeno.goo.registry.GooAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import java.util.ArrayList;
import java.util.List;

/**
 * How a Sculker moves. Pressing sneak and jump together enters the dive,
 * and from then on every solid block but bedrock is swimmable: the
 * body passes through stone with no key pressed into a block, weightless,
 * the movement input driving it along the look, jump lifting it and sneak
 * sinking it. The diver leaves the dive by rising out through a block's top
 * face and jumping clear, which stands it on that block. Sculker swims the
 * ground alone: a body neither inside a solid block nor just over a top face
 * is out of the dive whatever it holds, a walker again that falls, lands and
 * takes fall damage as any player does, until sneak and jump on the ground
 * take it back in. Bedrock is a hard
 * wall a diver stops at from any side, and one it never stays trapped in:
 * a body already inside bedrock moves out of it freely. The world's floor
 * and ceiling hold a diver from leaving the world and never from coming
 * back into it, so a diver below the floor rises back in on jump and no
 * diver is trapped under the ground, creative included.
 * decision sculker-swims-solid-blocks-and-surfaces-at-will
 * decision diagnose-and-correct-sculker-flying-through-air
 */
public final class SculkerSwim {

    /** The speed the swim drives toward under full input, in blocks per tick. */
    static final double SWIM_SPEED = 0.36;
    /** The share of the way the swim turns toward the driven speed each tick. */
    static final double SWIM_STEER = 0.25;
    /** How far the box shrinks before asking whether it is inside a solid block, so a body resting on a face is not. */
    private static final double INSIDE_INSET = 1.0E-7;
    /**
     * How far below the feet a diver looks for the ground it swims in: past
     * the height of a jump, so the jump that sneak and jump start the dive
     * with leaves the diver over the ground it sinks into.
     */
    private static final double SURFACE_REACH = 1.3;
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
     * and jump together take it in while the brew stands, rising out of a top
     * face with jump held takes it out, and so does leaving the ground. The
     * state syncs to its client.
     *
     * @param player the player
     */
    public static void updateDive(ServerPlayer player) {
        Sculker sculker = player.getData(GooAttachments.SCULKER);
        if (!sculker.diving() && !dives(player)) {
            return;
        }
        boolean diving = nextDiving(sculker.diving() && dives(player), dives(player), player.getLastClientInput(),
                inTheGround(player), overATopFace(player));
        if (diving != sculker.diving()) {
            player.setData(GooAttachments.SCULKER, sculker.withDiving(diving));
        }
    }

    /**
     * Whether a player is in the dive next tick: never with its body clear of
     * the ground, neither inside a solid block nor over a top face, whatever
     * it holds.
     *
     * @param diving      whether it is in the dive now
     * @param stands      whether its brew stands
     * @param keys        its held keys
     * @param inside      whether its body is in the ground: inside a solid block or below the world's floor
     * @param overATopFace whether a block's top face lies under its feet within a jump's height
     * @return true where it stays in or enters the dive
     */
    static boolean nextDiving(boolean diving, boolean stands, Input keys, boolean inside, boolean overATopFace) {
        if (!stands || !inside && !overATopFace) {
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
     * Holds a diver in the swimming pose, which the pose update inside stone
     * could never fit, so the diver swims through stone with the swim's own
     * animation; once out of the dive, lets the pose go back to the player's
     * own, leaving any pose another source forced.
     *
     * @param player the player
     * @param diving whether it is in the dive this tick
     */
    public static void holdTheSwimPose(Player player, boolean diving) {
        if (diving) {
            player.setForcedPose(Pose.SWIMMING);
        } else if (player.getForcedPose() == Pose.SWIMMING) {
            player.setForcedPose(null);
        }
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
     * Whether a player's body is in the ground: inside a solid block, or
     * below the world's floor, which holds a diver as the ground does so it
     * rises back into the world rather than falling out of it.
     *
     * @param player the player
     * @return true where its body is inside a solid block or below the floor
     */
    private static boolean inTheGround(Player player) {
        return insideSolid(player) || player.getBoundingBox().minY < player.level().getMinY();
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
     * The velocity a diver's first stroke steers from as it enters the dive:
     * its own across the ground, its fall or rise dropped, so the dive starts
     * weightless and no gravity carried in draws it down.
     *
     * @param player the diver
     * @return the velocity the first stroke steers from
     */
    public static Vec3 enteringStroke(Player player) {
        Vec3 moving = player.getDeltaMovement();
        return new Vec3(moving.x, 0, moving.z);
    }

    /**
     * One tick of a diver's swim: the velocity it moves at next, steered from
     * the stroke before rather than from the velocity the tick's gravity has
     * pulled down, so a diver is weightless and hangs where it stops, with its
     * rise or fall stopped where it would carry the body out past the world's
     * floor or ceiling, never where it brings the body back in.
     *
     * @param player     the diver
     * @param lastStroke the velocity the stroke before set, or {@link #enteringStroke} as it enters the dive
     * @param forward    the forward input, -1 to 1
     * @param strafe     the strafe input, -1 to 1, left positive
     * @param rise       the rise input, jump 1, sneak -1
     * @return the velocity the swim moves the diver at
     */
    public static Vec3 swimStroke(Player player, Vec3 lastStroke, double forward, double strafe, double rise) {
        Vec3 swim = swum(lastStroke, forward, strafe, rise, player.getLookAngle());
        AABB body = player.getBoundingBox();
        Level level = player.level();
        Vec3 inWorld = new Vec3(swim.x,
                heldInTheWorld(swim.y, body.minY, body.maxY, level.getMinY(), level.getMaxY() + 1), swim.z);
        return heldOutOfBedrock(level, body, inWorld);
    }

    /**
     * A move kept from carrying a body into bedrock: each axis in turn, as
     * vanilla collides, stops at the face of any bedrock the body is not
     * already inside, so bedrock walls a diver off from every side and a body
     * already inside bedrock moves out freely.
     *
     * @param level the level
     * @param body  the body's box
     * @param move  the move
     * @return the move bedrock allows
     */
    static Vec3 heldOutOfBedrock(Level level, AABB body, Vec3 move) {
        List<VoxelShape> walls = bedrockAhead(level, body, move);
        if (walls.isEmpty()) {
            return move;
        }
        double up = Shapes.collide(Direction.Axis.Y, body, walls, move.y);
        AABB risen = body.move(0, up, 0);
        double east = Shapes.collide(Direction.Axis.X, risen, walls, move.x);
        double south = Shapes.collide(Direction.Axis.Z, risen.move(east, 0, 0), walls, move.z);
        return new Vec3(east, up, south);
    }

    /**
     * The bedrock a move could carry a body into: every bedrock block within
     * the move's reach that the body does not already overlap.
     *
     * @param level the level
     * @param body  the body's box
     * @param move  the move
     * @return each such block's shape, placed in the world
     */
    private static List<VoxelShape> bedrockAhead(Level level, AABB body, Vec3 move) {
        AABB inside = body.deflate(INSIDE_INSET);
        AABB reach = body.expandTowards(move);
        List<VoxelShape> walls = new ArrayList<>();
        for (BlockPos cell : BlockPos.betweenClosed(BlockPos.containing(reach.minX, reach.minY, reach.minZ),
                BlockPos.containing(reach.maxX, reach.maxY, reach.maxZ))) {
            if (level.getBlockState(cell).is(Blocks.BEDROCK) && !inside.intersects(new AABB(cell))) {
                walls.add(Shapes.block().move(cell.getX(), cell.getY(), cell.getZ()));
            }
        }
        return walls;
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
