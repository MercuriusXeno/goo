package com.mercuriusxeno.goo.ability.program;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The size of a blinking body, which every spot it might land in must hold.
 * Decision blink-lands-safely-costed-by-distance.
 *
 * @param width     the body's width in blocks, the same along X and Z
 * @param height    the body's height in blocks
 * @param eyeHeight the eye's height above the feet in blocks
 */
public record BlinkBody(double width, double height, double eyeHeight) {

    private static final double HALF = 0.5;

    /**
     * The body an entity has in its current pose.
     *
     * @param entity the blinking entity
     * @return its body
     */
    public static BlinkBody of(Entity entity) {
        return new BlinkBody(entity.getBbWidth(), entity.getBbHeight(), entity.getEyeHeight());
    }

    /**
     * The box the body fills standing with its feet at a point.
     *
     * @param feet the feet, centered under the body
     * @return the box
     */
    public AABB boxAt(Vec3 feet) {
        double half = width * HALF;
        return new AABB(feet.x() - half, feet.y(), feet.z() - half, feet.x() + half, feet.y() + height,
                feet.z() + half);
    }

    /**
     * The middle of the body standing with its feet at a point.
     *
     * @param feet the feet
     * @return the body's center
     */
    Vec3 centerAt(Vec3 feet) {
        return feet.add(0, height * HALF, 0);
    }
}
