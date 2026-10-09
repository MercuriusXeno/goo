package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.hex.RandomEnchantment;
import com.mojang.datafixers.util.Unit;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
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
    private static final float PERCENT = 100;

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
     * phase: {@code consume_blocks radius=3}.
     */
    public static final LeafStepType<Expr> CONSUME_BLOCKS = StepType.of("consume_blocks", "radius", CONSUMED_GOO,
            (radius, context) -> {
                context.hostAs(ConsumedGooHost.class).consumeValuedBlocks(radius.evaluateInt(context));
                return true;
            });

    /**
     * Drops the goo total the host consumed as goo items at the anchor,
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
    public static final LeafStepType<Expr> COURT = StepType.of("court", "chance", TARGET, (chance, context) -> {
        TargetHost host = context.hostAs(TargetHost.class);
        if (host.target() instanceof Animal animal && animal.getAge() == 0 && animal.canFallInLove()
                && courts(chance.evaluateFloat(context), animal.getRandom().nextFloat())) {
            animal.setInLove(host.thrower() instanceof Player player ? player : null);
        }
        return true;
    });

    /**
     * Gives the host's target, when a player, an enchanted book holding one
     * random enchantment at level one; Enchant runs {@code enchant_book}
     * on the invoking player once its book is consumed.
     * enchant-book-with-a-purple-afterimage
     */
    public static final LeafStepType<Unit> ENCHANT_BOOK = StepType.of("enchant_book", NO_PARAMS, TARGET,
            (none, context) -> {
                if (context.hostAs(TargetHost.class).target() instanceof Player player) {
                    RandomEnchantment.giveBook(player);
                }
                return true;
            });

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
