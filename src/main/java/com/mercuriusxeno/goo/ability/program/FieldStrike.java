package com.mercuriusxeno.goo.ability.program;

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
     * Returns this strike one tick older.
     *
     * @return the aged strike
     */
    public FieldStrike aged() {
        return new FieldStrike(entityId, x, y, z, age + 1);
    }
}
