package com.mercuriusxeno.goo.ability.program;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * One strike a field effect has in flight: the entity it chose, the point
 * it aims at, and the ticks since. The point follows the entity's body
 * center each tick, so Urchin's spear lands where the mob stands when its
 * damage lands rather than where the mob stood when it was picked.
 * urchin-rests-bristled-and-spears
 *
 * @param entityId the struck entity's id in its level
 * @param x        the aimed point's x, the entity's body center as of the last tick
 * @param y        the aimed point's y
 * @param z        the aimed point's z
 * @param age      ticks since the strike was chosen
 */
public record FieldStrike(int entityId, float x, float y, float z, int age) {

    /**
     * Starts a strike on the selected entity, aimed at its body center.
     *
     * @param entity the selected entity
     * @return the new strike, zero ticks old
     */
    static FieldStrike aimedAt(LivingEntity entity) {
        return new FieldStrike(entity.getId(), 0, 0, 0, 0).reaimedAt(entity.getBoundingBox().getCenter());
    }

    /**
     * Re-aims a strike in flight at its entity's body center now.
     *
     * @param strike the strike
     * @param entity the entity it struck
     * @return the strike aimed where the entity stands
     */
    static FieldStrike followed(FieldStrike strike, LivingEntity entity) {
        return strike.reaimedAt(entity.getBoundingBox().getCenter());
    }

    /**
     * Returns this strike aimed at a new point, its entity and age kept.
     *
     * @param center the entity's body center now
     * @return the re-aimed strike
     */
    public FieldStrike reaimedAt(Vec3 center) {
        return new FieldStrike(entityId, (float) center.x(), (float) center.y(), (float) center.z(), age);
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
