package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.hex.RandomEnchantment;
import com.mercuriusxeno.goo.ability.pulse.StunEvents;
import com.mercuriusxeno.goo.ability.revert.RevertEvents;
import com.mojang.datafixers.util.Unit;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

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
    private static final Set<HostCapability> IGNITE_LANDING = Set.of(HostCapability.IGNITE);
    private static final Predicate<Object> ANY = entity -> true;

    /**
     * Idles for a number of ticks, then finishes. {@code wait ticks=2} lets
     * two ticks pass before the next step runs.
     */
    public static final LeafStepType<Expr> WAIT = StepType.of("wait", "ticks",
            Set.of(HostCapability.TICKING), (ticks, context) -> context.stepTicks() >= ticks.evaluateInt(context))
            .takingNone();

    /**
     * Removes the host's target from the world without a death, drops or a
     * loot roll.
     */
    public static final LeafStepType<Unit> DISCARD = StepType.of("discard", NO_PARAMS, TARGET, (none, context) -> {
        context.hostAs(TargetHost.class).target().discard();
        return true;
    });

    /**
     * Toggles the host's target's AI; a target that is not a mob has no AI
     * to toggle and is left alone, so a program wraps
     * {@code set_ai enabled=false} in {@code target where=[mob]}.
     */
    public static final LeafStepType<Boolean> SET_AI = StepType.onTarget("set_ai", ENABLED, Mob.class, ANY,
            (mob, enabled, context) -> mob.setNoAi(!enabled));

    /**
     * Makes the host's target a baby or an adult; a target with no baby
     * form is left as it is, so a program guards the step with
     * {@code target where=[has_baby_form, not_baby]}.
     */
    public static final LeafStepType<Boolean> SET_BABY = StepType.onTarget("set_baby", ENABLED, Mob.class, ANY,
            (mob, enabled, context) -> mob.setBaby(enabled));

    /**
     * Toggles the host's target's invulnerability.
     */
    public static final LeafStepType<Boolean> SET_INVULNERABLE = StepType.of("set_invulnerable", ENABLED, TARGET,
            (enabled, context) -> {
                context.hostAs(TargetHost.class).target().setPermanentlyInvulnerable(enabled);
                return true;
            });

    /**
     * Freezes the host's target while Revert's stream holds it, the freeze
     * ending a few ticks after the stream lets go; a target that is not a
     * mob is left alone.
     * rewind-fills-while-held
     */
    public static final LeafStepType<Unit> REVERT_HOLD = StepType.onTarget("revert_hold", NO_PARAMS, Mob.class, ANY,
            (mob, none, context) -> RevertEvents.hold(mob, mob.level().getGameTime()));

    /**
     * Sets the host's target on fire for a number of seconds; blaze ignite
     * is {@code ignite seconds=10} on the struck entity.
     */
    public static final LeafStepType<Expr> IGNITE = TargetEffectStep.of("ignite", "seconds",
            (target, seconds, context) -> target.igniteForSeconds(seconds.evaluateInt(context)));

    /**
     * Lights a fire in the landing cell; Ignite's blob lights one where it
     * lands: {@code light_fire}.
     * ignite-fire-and-explosive-combo
     */
    public static final LeafStepType<Unit> LIGHT_FIRE = StepType.of("light_fire", NO_PARAMS, IGNITE_LANDING,
            (none, context) -> {
                context.hostAs(IgniteHost.class).lightFire();
                return true;
            }).takingNone();

    /**
     * Heals the host's target by an amount of health points in pulses:
     * Heal streams {@code heal amount every} over each living thing in its
     * cone and its caster every tick it is held, and each target takes the
     * amount on the first tick the heal reaches it and once each
     * {@code every} ticks after. An undead target, the caster among them,
     * is harmed by the amount instead.
     * vitality-waves-regenerate-and-court
     * heal-harms-an-undead-target
     * diagnose-and-correct-heal-spamming-zombie-noises
     */
    public static final LeafStepType<HealPulse> HEAL = new LeafStepType<>("heal", HealPulse.CODEC,
            pulse -> Stream.of(pulse.amount()), TARGET, (pulse, context) -> {
                TargetHost host = context.hostAs(TargetHost.class);
                LivingEntity target = host.target();
                if (pulse.dueOn(target, target.level().getGameTime())) {
                    UndeadInversion.heal(target, pulse.amount().evaluateFloat(context), host.thrower());
                }
                return true;
            });

    /**
     * Ticks the redstone device the host's blob landed on, as one pulse of
     * power would: Zap is {@code power_pulse}.
     * zap-ticks-the-device-and-stuns
     */
    public static final LeafStepType<Unit> POWER_PULSE = StepType.of("power_pulse", NO_PARAMS,
            Set.of(HostCapability.POWER_PULSE), (none, context) -> {
                context.hostAs(PowerPulseHost.class).powerPulse();
                return true;
            }).takingNone();

    /**
     * Stuns the host's target for a number of ticks: it drops its target and
     * what it was doing, and its AI stands off until the stun ends. Zap
     * stuns what stands at its landing with {@code stun ticks=60}.
     * zap-ticks-the-device-and-stuns
     */
    public static final LeafStepType<Expr> STUN = TargetEffectStep.of("stun", "ticks",
            (target, ticks, context) -> StunEvents.stun(target, ticks.evaluateInt(context)))
            .taking(Mob.class::isInstance);

    /**
     * Gives the host's target, when a player, an enchanted book holding one
     * random enchantment at level one; Enchant runs {@code enchant_book}
     * on the invoking player once its book is consumed.
     * enchant-book-with-a-purple-afterimage
     */
    public static final LeafStepType<Unit> ENCHANT_BOOK = StepType.onTarget("enchant_book", NO_PARAMS, Player.class,
            ANY, (player, none, context) -> RandomEnchantment.giveBook(player));

    private LeafSteps() {
    }
}
