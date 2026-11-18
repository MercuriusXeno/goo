package com.mercuriusxeno.goo.ability.zone;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import java.util.function.Predicate;

/**
 * How far before its caster a doorway stands: as far as the space ahead of
 * them is clear, up to its own stand-off, so a wall in front of them pulls
 * it nearer rather than through the wall. It walks out the way they face a
 * sixteenth of a block at a time, at their feet's height and their head's,
 * and stops a sixteenth short of the first solid block.
 * decision tartarus-draws-its-own-portal-with-the-nether-portal-texture
 */
public final class DoorStandOff {

    /** The step the walk takes, a texel. */
    static final double STEP = 1.0 / 16;
    /** The heights above the feet it checks: the feet's block and the head's. */
    private static final double FEET_HEIGHT = 0.5;
    private static final double HEAD_HEIGHT = 1.5;

    private DoorStandOff() {
    }

    /**
     * The clear stand-off ahead of a caster.
     *
     * @param solid whether a block stops the doorway
     * @param feet  the caster's feet
     * @param yaw   the caster's yaw, in degrees
     * @param most  the doorway's own stand-off, in blocks
     * @return the stand-off, 0 to the most
     */
    public static double clearAhead(Predicate<BlockPos> solid, Vec3 feet, float yaw, double most) {
        double radians = Math.toRadians(yaw);
        Vec3 ahead = new Vec3(-Math.sin(radians), 0, Math.cos(radians));
        int steps = (int) Math.round(most / STEP);
        for (int step = 1; step <= steps; step++) {
            Vec3 at = feet.add(ahead.scale(step * STEP));
            if (solid.test(BlockPos.containing(at.add(0, FEET_HEIGHT, 0)))
                    || solid.test(BlockPos.containing(at.add(0, HEAD_HEIGHT, 0)))) {
                return Math.max(0, (step - 1) * STEP);
            }
        }
        return most;
    }
}
