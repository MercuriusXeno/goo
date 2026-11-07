package com.mercuriusxeno.goo.ability.program;

import net.minecraft.world.entity.Entity;

/**
 * A step acting on no entity: it plays a sound, draws particles or an
 * overlay, keeps a count, waits, or changes blocks alone, so the glove's
 * aim outlines nothing for it.
 * decision steps-declare-the-entities-they-take
 */
public interface TakesNoEntity extends Step {

    @Override
    default boolean takesEntity(Entity entity) {
        return false;
    }
}
