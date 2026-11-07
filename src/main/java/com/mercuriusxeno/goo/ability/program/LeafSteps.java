package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.hex.RandomEnchantment;
import com.mercuriusxeno.goo.ability.pulse.StunEvents;
import com.mercuriusxeno.goo.ability.rewind.RewindEvents;
import com.mojang.datafixers.util.Unit;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import java.util.Set;
import java.util.function.Predicate;

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
    private static final Set<HostCapability> HOARD = Set.of(HostCapability.HOARD);
    private static final Set<HostCapability> IGNITE_LANDING = Set.of(HostCapability.IGNITE);
    private static final float PERCENT = 100;
    private static final Predicate<Object> ANY = entity -> true;

    /**
     * Idles for a number of ticks, then finishes. {@code wait ticks=2} lets
     * two ticks pass before the next step runs.
     */
    public static final LeafStepType<Expr> WAIT = StepType.of("wait", "ticks",
            Set.of(HostCapability.TICKING), (ticks, context) -> context.stepTicks() >= ticks.evaluateInt(context))
            .takingNone();

    /**
     * Takes every breakable block within a sphere around the host anchor
     * into the hoard the host keeps, each as its silk-touched drops; the
     * nether black hole consumes its blast sphere as it leaves its expand
     * phase: {@code consume_blocks radius=3}.
     */
    public static final LeafStepType<Expr> CONSUME_BLOCKS = StepType.of("consume_blocks", "radius", HOARD,
            (radius, context) -> {
                context.hostAs(HoardHost.class).hoardBlocks(radius.evaluateInt(context));
                return true;
            }).takingNone();

    /**
     * Leaves the host's hoard as one compression sphere at the anchor,
     * emptying it; the nether black hole drops what it pulled in once it
     * has contracted: {@code drop_sphere}.
     */
    public static final LeafStepType<Unit> DROP_SPHERE = StepType.of("drop_sphere", NO_PARAMS, HOARD,
            (none, context) -> {
                context.hostAs(HoardHost.class).dropSphere();
                return true;
            }).takingNone();

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
                context.hostAs(TargetHost.class).target().setInvulnerable(enabled);
                return true;
            });

    /**
     * Freezes the host's target while Rewind's stream holds it, the freeze
     * ending a few ticks after the stream lets go; a target that is not a
     * mob is left alone.
     * rewind-fills-while-held
     */
    public static final LeafStepType<Unit> REWIND_HOLD = StepType.onTarget("rewind_hold", NO_PARAMS, Mob.class, ANY,
            (mob, none, context) -> RewindEvents.hold(mob, mob.level().getGameTime()));

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
     * Sets off the explosive ability block the landing struck as a fiery
     * explosion; Ignite landing on a Lurker: {@code detonate_explosive power=2.5}.
     * ignite-fire-and-explosive-combo
     */
    public static final LeafStepType<Expr> DETONATE_EXPLOSIVE = StepType.of("detonate_explosive", "power", IGNITE_LANDING,
            (power, context) -> {
                context.hostAs(IgniteHost.class).detonateExplosive(power.evaluateFloat(context));
                return true;
            }).takingNone();

    /**
     * Heals the host's target by an amount of health points; vitality
     * streams {@code heal amount=0.1} over each living thing in its cone
     * and its caster every tick it is held.
     * vitality-waves-regenerate-and-court
     */
    public static final LeafStepType<Expr> HEAL = TargetEffectStep.of("heal", "amount",
            (target, amount, context) -> target.heal(amount.evaluateFloat(context)));

    /**
     * Puts the host's target in love on a percent roll when it is an animal
     * an empty-handed feed could breed now: grown, off its breeding
     * cooldown and out of love. Vitality rolls {@code court chance=0.25} on
     * every animal its waves wash over each tick.
     * vitality-waves-regenerate-and-court
     */
    public static final LeafStepType<Expr> COURT = StepType.onTarget("court", "chance", Animal.class,
            animal -> !animal.isBaby(), (animal, chance, context) -> {
                // the client reads no breeding cooldown, so the aim takes every adult and the roll waits for one off it
                if (animal.getAge() == 0 && animal.canFallInLove()
                        && courts(chance.evaluateFloat(context), animal.getRandom().nextFloat())) {
                    Entity thrower = context.hostAs(TargetHost.class).thrower();
                    animal.setInLove(thrower instanceof Player player ? player : null);
                }
            });

    /**
     * Starts aging the block the host's blob landed on: Yore's aging blob is
     * {@code age}.
     * old-blob-ages-valuables-slowly
     */
    public static final LeafStepType<Unit> AGE = StepType.of("age", NO_PARAMS,
            Set.of(HostCapability.AGING), (none, context) -> {
                context.hostAs(AgingHost.class).startAging();
                return true;
            }).takingNone();

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

    /**
     * Whether a court roll lands: a roll in [0, 1) lands under a percent chance.
     *
     * @param chancePercent the percent chance the step names
     * @param roll          the uniform roll in [0, 1)
     * @return true when the roll lands
     */
    static boolean courts(float chancePercent, float roll) {
        return roll * PERCENT < chancePercent;
    }
}
