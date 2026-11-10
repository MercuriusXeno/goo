package com.mercuriusxeno.goo.ability.reap;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Where Reap's slashes fly: each in a random heading inside the cone ahead
 * of the slasher's look, pitched from level down to the dive that meets the
 * ground at the slash's reach, and the cells its path crosses on the way.
 * reap-is-crissaegrim-slashes-that-harvest-and-cut
 */
public final class ReapSlashes {

    /** How far apart the path is sampled, in blocks, fine enough to cross every cell it passes through. */
    private static final double PATH_STEP = 0.05;
    /** The share of the cone either side of the look. */
    private static final double HALF = 0.5;

    private ReapSlashes() {
    }

    /**
     * A slash's heading, in Minecraft's rotation convention: yaw about the
     * vertical, and pitch positive downward.
     *
     * @param yaw   the heading's yaw, in degrees
     * @param pitch the heading's pitch below level, in degrees
     */
    public record Heading(float yaw, float pitch) {

        /**
         * The unit vector the slash flies along.
         *
         * @return the direction
         */
        public Vec3 direction() {
            return Vec3.directionFromRotation(pitch, yaw);
        }
    }

    /**
     * A random heading for one slash.
     *
     * @param lookYaw     the slasher's yaw, the cone's centre, in degrees
     * @param coneDegrees the cone's width, edge to edge, in degrees
     * @param dropDegrees the steepest pitch below level a slash dives at, in degrees
     * @param random      the random source
     * @return the heading
     */
    public static Heading sample(float lookYaw, double coneDegrees, double dropDegrees, RandomSource random) {
        float yaw = (float) (lookYaw + (random.nextDouble() - HALF) * coneDegrees);
        float pitch = (float) (random.nextDouble() * dropDegrees);
        return new Heading(yaw, pitch);
    }

    /**
     * The steepest pitch a slash dives at: the one whose path, launched at a
     * height above the ground, meets the ground at its reach, so the lowest
     * slash runs down across the ground.
     *
     * @param launchHeight how far above the slasher's feet the slashes launch, in blocks
     * @param reach        how far a slash flies, in blocks
     * @return the pitch below level, in degrees
     */
    public static double dropLimit(double launchHeight, double reach) {
        return Math.toDegrees(Math.atan2(launchHeight, reach));
    }

    /**
     * The cells a straight path crosses, each once, nearest its start first.
     *
     * @param from where the path starts
     * @param to   where it ends
     * @return the cells crossed
     */
    public static List<BlockPos> cellsAlong(Vec3 from, Vec3 to) {
        Vec3 along = to.subtract(from);
        int samples = (int) Math.ceil(along.length() / PATH_STEP);
        Set<BlockPos> cells = new LinkedHashSet<>();
        for (int sample = 0; sample <= samples; sample++) {
            cells.add(BlockPos.containing(from.add(along.scale(samples == 0 ? 0 : (double) sample / samples))));
        }
        return List.copyOf(cells);
    }
}
