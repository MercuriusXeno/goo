package com.mercuriusxeno.goo.ability.program;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * One strike a field effect has in flight: the entity it chose, the point
 * it aimed at when it chose it, and the ticks since. The point stays fixed
 * so the metal spike stays rigid while the entity moves on.
 *
 * @param entityId the struck entity's id in its level
 * @param x        the aimed point's x, the entity's body center when chosen
 * @param y        the aimed point's y
 * @param z        the aimed point's z
 * @param age      ticks since the strike was chosen
 */
public record FieldStrike(int entityId, float x, float y, float z, int age) {

    /**
     * Starts a strike on the entity the host hands over, aimed at its body
     * center (decision step-tick-holds-effect).
     *
     * @param target the host bound to the selected entity
     * @return the new strike, zero ticks old
     */
    static FieldStrike aimedAt(TargetHost target) {
        LivingEntity entity = target.target();
        Vec3 center = entity.getBoundingBox().getCenter();
        return new FieldStrike(entity.getId(), (float) center.x(), (float) center.y(), (float) center.z(), 0);
    }

    /**
     * Returns this strike one tick older.
     *
     * @return the aged strike
     */
    public FieldStrike aged() {
        return new FieldStrike(entityId, x, y, z, age + 1);
    }
}
