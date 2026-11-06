package com.mercuriusxeno.goo.ability.program;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import java.util.OptionalInt;

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

    /**
     * The duration a drunk brew holds its ability for, which a duration-bearing
     * step takes over its own; empty for every program the brew did not start.
     * decision brew-grants-the-self-ability-for-an-hour
     *
     * @return the brew's duration in ticks, or empty
     */
    default OptionalInt brewDuration() {
        return OptionalInt.empty();
    }

    /**
     * Sets the target moving and marks the motion for the target's client.
     *
     * @param motion the velocity to set, in blocks per tick
     */
    default void push(Vec3 motion) {
        LivingEntity target = target();
        target.setDeltaMovement(motion);
        target.hurtMarked = true;
    }
}
