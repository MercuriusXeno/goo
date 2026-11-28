package com.mercuriusxeno.goo.lunar;

import net.minecraft.world.phys.Vec3;
import java.util.Optional;

/**
 * Where a body comes out on crossing lunar's edge: a body past +7,500,000 or -7,500,000 on x or z comes out the same
 * distance in from the other edge, at its own height, facing as it faced and moving as it moved.
 * decision moon-wraps-at-its-border-like-the-zone
 */
public final class LunarCrossing {

    private LunarCrossing() {
    }

    /**
     * A body carried across the seam.
     *
     * @param position where it comes out
     * @param motion   how it moves there
     * @param yaw      its facing round, in degrees
     * @param pitch    its facing up or down, in degrees
     */
    public record Carry(Vec3 position, Vec3 motion, float yaw, float pitch) {
    }

    /**
     * Carries a body that has crossed the seam to the other side.
     *
     * @param position where the body stands
     * @param motion   how it moves
     * @param yaw      its facing round, in degrees
     * @param pitch    its facing up or down, in degrees
     * @return where it comes out, or empty for a body still inside the moon's span
     */
    public static Optional<Carry> across(Vec3 position, Vec3 motion, float yaw, float pitch) {
        double x = LunarSeam.wrap(position.x);
        double z = LunarSeam.wrap(position.z);
        if (x == position.x && z == position.z) {
            return Optional.empty();
        }
        return Optional.of(new Carry(new Vec3(x, position.y, z), motion, yaw, pitch));
    }
}
