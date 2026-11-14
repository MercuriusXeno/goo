package com.mercuriusxeno.goo.item;

import net.minecraft.world.phys.Vec3;

/**
 * A fleck of goo leaving a block face it struck: its speed along the face's
 * normal reversed and scaled by its type's elasticity, its speed along the
 * face kept.
 * decision flecks-move-by-flexible-goo-physics-and-stay-items
 * decision each-goo-type-carries-elasticity-and-viscosity
 */
public final class FleckRebound {

    private FleckRebound() {
    }

    /**
     * The velocity a fleck leaves a face with.
     *
     * @param velocity   the velocity it struck the face with
     * @param normal     the face's unit normal, either way along it
     * @param elasticity the share of its speed into the face it keeps, 0 to 1
     * @return the velocity it leaves with
     */
    public static Vec3 rebound(Vec3 velocity, Vec3 normal, float elasticity) {
        double into = velocity.dot(normal);
        return velocity.subtract(normal.scale(into * (1 + elasticity)));
    }
}
