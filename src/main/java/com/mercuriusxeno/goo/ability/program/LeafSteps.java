package com.mercuriusxeno.goo.ability.program;

import com.mojang.datafixers.util.Unit;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Mob;
import java.util.Set;

/**
 * The leaf steps whose frame {@link LeafStep} carries, each its body and
 * one definition, registered by {@link StepTypes} (decision
 * capability-interfaces-derive-host-kind).
 */
public final class LeafSteps {

    private static final String FIELD_ENABLED = "enabled";
    private static final MapCodec<Boolean> ENABLED = Codec.BOOL.fieldOf(FIELD_ENABLED);
    private static final MapCodec<Unit> NO_PARAMS = MapCodec.unit(Unit.INSTANCE);
    private static final Set<HostCapability> TARGET = Set.of(HostCapability.TARGET);
    private static final Set<HostCapability> CONSUMED_GOO = Set.of(HostCapability.CONSUMED_GOO);

    /**
     * Idles for a number of ticks, then finishes. {@code wait ticks=2} lets
     * two ticks pass before the next step runs.
     */
    public static final LeafStepType<Expr> WAIT = StepType.of("wait", "ticks",
            Set.of(HostCapability.TICKING), (ticks, context) -> context.stepTicks() >= ticks.evaluateInt(context));

    /**
     * Removes every block with a goo value within a sphere around the host
     * anchor, adding each block's goo to the total the host keeps; the
     * nether black hole consumes its blast sphere as it leaves its expand
     * phase: {@code consume_blocks radius="1 + 2 * stacks"}.
     */
    public static final LeafStepType<Expr> CONSUME_BLOCKS = StepType.of("consume_blocks", "radius", CONSUMED_GOO,
            (radius, context) -> {
                context.hostAs(ConsumedGooHost.class).consumeValuedBlocks(radius.evaluateInt(context));
                return true;
            });

    /**
     * Drops the goo total the host consumed as blob items at the anchor,
     * emptying the total; the nether black hole pops what it consumed once
     * it has contracted: {@code drop_consumed}.
     */
    public static final LeafStepType<Unit> DROP_CONSUMED = StepType.of("drop_consumed", NO_PARAMS, CONSUMED_GOO,
            (none, context) -> {
                context.hostAs(ConsumedGooHost.class).dropConsumedGoo();
                return true;
            });

    /**
     * Removes the host's target from the world without a death, drops or a
     * loot roll; aeon's ritual discards the mob it turned into its spawn
     * egg (decision aeon-mob-ritual-drops-spawn-egg).
     */
    public static final LeafStepType<Unit> DISCARD = StepType.of("discard", NO_PARAMS, TARGET, (none, context) -> {
        context.hostAs(TargetHost.class).target().discard();
        return true;
    });

    /**
     * Toggles the host's target's AI; a target that is not a mob has no AI
     * to toggle and is left alone, so pulse short circuit wraps
     * {@code set_ai enabled=false} in {@code target where=[mob]}.
     */
    public static final LeafStepType<Boolean> SET_AI = StepType.of("set_ai", ENABLED, TARGET, (enabled, context) -> {
        if (context.hostAs(TargetHost.class).target() instanceof Mob mob) {
            mob.setNoAi(!enabled);
        }
        return true;
    });

    /**
     * Makes the host's target a baby or an adult; a target with no baby
     * form is left as it is, so aeon's ritual guards the step with
     * {@code target where=[has_baby_form, not_baby]} (decision
     * aeon-mob-ritual-drops-spawn-egg).
     */
    public static final LeafStepType<Boolean> SET_BABY = StepType.of("set_baby", ENABLED, TARGET,
            (enabled, context) -> {
                if (context.hostAs(TargetHost.class).target() instanceof Mob mob) {
                    mob.setBaby(enabled);
                }
                return true;
            });

    /**
     * Toggles the host's target's invulnerability; aeon time stop is
     * {@code set_ai enabled=false} then {@code set_invulnerable enabled=true}.
     */
    public static final LeafStepType<Boolean> SET_INVULNERABLE = StepType.of("set_invulnerable", ENABLED, TARGET,
            (enabled, context) -> {
                context.hostAs(TargetHost.class).target().setInvulnerable(enabled);
                return true;
            });

    /**
     * Sets the host's target on fire for a number of seconds; blaze ignite
     * is {@code ignite seconds=10} on the struck entity.
     */
    public static final LeafStepType<Expr> IGNITE = TargetEffectStep.of("ignite", "seconds",
            (target, seconds, context) -> target.igniteForSeconds(seconds.evaluateInt(context)));

    /**
     * Sets the host's target to a fraction of its current health, bypassing
     * damage; nether wither is {@code set_health fraction=0.5}.
     */
    public static final LeafStepType<Expr> SET_HEALTH = TargetEffectStep.of("set_health", "fraction",
            (target, fraction, context) -> target.setHealth(target.getHealth() * fraction.evaluateFloat(context)));

    /**
     * Adds to the host's target's frozen ticks; a full freeze stands at
     * 140, so frost snap is {@code freeze_ticks add=140}.
     */
    public static final LeafStepType<Expr> FREEZE_TICKS = TargetEffectStep.of("freeze_ticks", "add",
            (target, add, context) -> target.setTicksFrozen(target.getTicksFrozen() + add.evaluateInt(context)));

    private LeafSteps() {
    }
}
