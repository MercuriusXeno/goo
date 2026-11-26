package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.Delivery;
import com.mercuriusxeno.goo.throwing.ConeShape;
import net.minecraft.world.phys.Vec3;

/**
 * The cone a spray pours through this frame, the one input every spray visual draws from: a glove hands the round
 * cone of the ability's Delivery, and a nozzle hands the cone it shapes.
 * decision spray-abilities-render-procedurally-bound-to-their-cone
 * decision cone-style-abilities-are-reviewed-for-consistency
 *
 * @param apex  where the spray leaves, in world coordinates
 * @param axis  the unit vector it pours along
 * @param shape its widths across and up and down
 * @param reach blocks from the apex it reaches in open air
 */
public record SprayCone(Vec3 apex, Vec3 axis, ConeShape shape, double reach) {

    /**
     * The cone a glove pours an ability through: the round cone of its delivery's cone degrees, out to its range.
     *
     * @param delivery the ability's delivery
     * @param apex     where the cast leaves, in world coordinates
     * @param axis     the unit vector it pours along
     * @return the cone
     */
    public static SprayCone ofGlove(Delivery delivery, Vec3 apex, Vec3 axis) {
        return new SprayCone(apex, axis, ConeShape.round(delivery.coneDegrees()), delivery.range());
    }
}
