package com.mercuriusxeno.goo.ability.program;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

/**
 * A host bound to a living entity the effect steps act on (capability
 * {@link HostCapability#TARGET}); each effect step's tick acts on the
 * target directly (decision step-tick-holds-effect).
 */
public interface TargetHost extends StepHost {

    /**
     * Returns the living entity the host's program acts on.
     *
     * @return the target entity
     */
    LivingEntity target();

    /**
     * Returns the entity that set the program on the target, which a
     * teleport toward or away from it reads.
     *
     * @return the thrower, or null when unknown
     */
    @Nullable Entity thrower();
}
