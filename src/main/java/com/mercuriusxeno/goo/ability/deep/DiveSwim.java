package com.mercuriusxeno.goo.ability.deep;

import com.mercuriusxeno.goo.registry.GooAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * How a diving player moves: while the dive stands it passes through solid
 * blocks wherever its body is already inside one, and wherever it presses
 * into one, forward into a wall, up into a ceiling with jump or down into
 * the ground with sneak, so it walks open ground as ever, enters stone at
 * will from any side and can always get back in from below. Once its body
 * stands clear in the open, the blocks hold it again. While it passes it
 * swims weightless, the way it swims in water: the movement input drives it
 * along the look, jump lifts it and sneak sinks it, and nothing in the stone
 * suffocates it. Bedrock and every block nothing can break hold a diver as
 * they hold anyone, and so does the bottom and the top of the world, so no
 * dive leaves the world.
 * decision dive-is-a-brew-of-warden-senses-and-free-swimming-through-solid-blocks
 */
public final class DiveSwim {

    /** The speed the swim drives toward under full input, in blocks per tick. */
    static final double SWIM_SPEED = 0.3;
    /** The share of the way the swim turns toward the driven speed each tick. */
    static final double SWIM_STEER = 0.25;
    /** How far the box shrinks before asking whether it is inside a solid block, so a body brushing a wall is not. */
    private static final double INSIDE_INSET = 0.05;
    /** How far a pressed direction probes past the body for stone to enter. */
    private static final double PRESS_REACH = 0.1;
    /** How far a box's far faces pull in before naming its cells, so a face lying on a cell's edge does not reach into it. */
    private static final double EDGE_INSET = 1.0E-7;
    private static final Vec3 UP = new Vec3(0, 1, 0);
    private static final Vec3 DOWN = new Vec3(0, -1, 0);

    /**
     * The keys the local client holds, which the client sets at its setup; a
     * server reads each player's keys off its last input packet instead.
     */
    private static Function<Player, Input> localKeys = player -> Input.EMPTY;

    private DiveSwim() {
    }

    /**
     * Hands the swim the local client's held keys.
     *
     * @param keys the keys a client player holds
     */
    public static void readLocalKeysFrom(Function<Player, Input> keys) {
        localKeys = keys;
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
     * @return true while its dive stands and it is inside solid blocks or presses into them
     */
    public static boolean passesThrough(Player player) {
        return dives(player) && (insideSolid(player) || pressesIntoStone(player, keysOf(player)));
    }

    private static Input keysOf(Player player) {
        return player instanceof ServerPlayer server ? server.getLastClientInput() : localKeys.apply(player);
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

    private static boolean pressesIntoStone(Player player, Input keys) {
        for (Vec3 pressed : pressedDirections(keys, player.getLookAngle())) {
            if (meetsEnterableStone(player.level(), player.getBoundingBox().move(pressed.scale(PRESS_REACH)))) {
                return true;
            }
        }
        return false;
    }

    /**
     * The directions a diver's held keys press it: forward and back along
     * the look's heading, left and right across it, up with jump and down
     * with sneak.
     *
     * @param keys the held keys
     * @param look the look direction
     * @return a unit direction for each key held
     */
    static List<Vec3> pressedDirections(Input keys, Vec3 look) {
        Vec3 heading = new Vec3(look.x, 0, look.z);
        Vec3 ahead = heading.lengthSqr() == 0 ? Vec3.ZERO : heading.normalize();
        Vec3 left = new Vec3(ahead.z, 0, -ahead.x);
        List<Vec3> pressed = new ArrayList<>();
        addWhen(pressed, keys.forward(), ahead);
        addWhen(pressed, keys.backward(), ahead.reverse());
        addWhen(pressed, keys.left(), left);
        addWhen(pressed, keys.right(), left.reverse());
        addWhen(pressed, keys.jump(), UP);
        addWhen(pressed, keys.shift(), DOWN);
        return pressed;
    }

    private static void addWhen(List<Vec3> pressed, boolean held, Vec3 direction) {
        if (held && direction.lengthSqr() > 0) {
            pressed.add(direction);
        }
    }

    /**
     * Whether a box meets a solid block a diver may enter, one whose
     * collision shape it overlaps and that is no world barrier.
     *
     * @param level the level
     * @param box   the box
     * @return true where the box meets enterable stone
     */
    private static boolean meetsEnterableStone(Level level, AABB box) {
        boolean meets = false;
        for (BlockPos cell : cellsOf(box)) {
            if (holdsADiver(level, cell)) {
                return false;
            }
            meets |= overlapsShape(level, cell, box);
        }
        return meets;
    }

    /**
     * Whether a cell holds a diver as it holds anyone: outside the world, or
     * a solid block nothing can break, as bedrock is.
     *
     * @param level the level
     * @param cell  the cell
     * @return true where a diver may not pass
     */
    static boolean holdsADiver(Level level, BlockPos cell) {
        if (level.isOutsideBuildHeight(cell)) {
            return true;
        }
        BlockState state = level.getBlockState(cell);
        return state.getDestroySpeed(level, cell) < 0 && !state.getCollisionShape(level, cell).isEmpty();
    }

    private static Iterable<BlockPos> cellsOf(AABB box) {
        return BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ),
                BlockPos.containing(box.maxX - EDGE_INSET, box.maxY - EDGE_INSET, box.maxZ - EDGE_INSET));
    }

    private static boolean overlapsShape(Level level, BlockPos cell, AABB box) {
        return level.getBlockState(cell).getCollisionShape(level, cell).toAabbs().stream()
                .anyMatch(part -> part.move(cell).intersects(box));
    }

    /**
     * One tick of a diver's swim: the velocity it moves at next, with each
     * axis of it that would carry its body into bedrock or out of the world
     * stopped.
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
        double y = heldAt(player.level(), body.move(0, swim.y, 0)) ? 0 : swim.y;
        double x = heldAt(player.level(), body.move(swim.x, y, 0)) ? 0 : swim.x;
        double z = heldAt(player.level(), body.move(x, y, swim.z)) ? 0 : swim.z;
        return new Vec3(x, y, z);
    }

    private static boolean heldAt(Level level, AABB box) {
        for (BlockPos cell : cellsOf(box)) {
            if (holdsADiver(level, cell)) {
                return true;
            }
        }
        return false;
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
