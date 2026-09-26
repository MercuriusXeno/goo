package com.mercuriusxeno.goo.ability.program;

import net.minecraft.world.entity.LivingEntity;
import java.util.Set;

/**
 * The frame of a one-number target effect: a leaf step that reads one
 * expression and hands it with the host's target to the effect, then
 * finishes (decision capability-interfaces-derive-host-kind).
 */
public final class TargetEffectStep {

    private TargetEffectStep() {
    }

    /**
     * Builds a one-number target effect kind.
     *
     * @param name   the type name as written in the JSON
     * @param field  the field the number is written under
     * @param effect what the number does to the target
     * @return the leaf kind
     */
    public static LeafStepType<Expr> of(String name, String field, Effect effect) {
        return StepType.of(name, field, Set.of(HostCapability.TARGET), (amount, context) -> {
            effect.apply(context.hostAs(TargetHost.class).target(), amount, context);
            return true;
        });
    }

    /**
     * What a one-number effect does to its target.
     */
    @FunctionalInterface
    public interface Effect {

        /**
         * Applies the effect.
         *
         * @param target  the host's target
         * @param amount  the step's number, evaluated here on the tick's scope
         * @param context the tick's scope
         */
        void apply(LivingEntity target, Expr amount, StepContext context);
    }
}
