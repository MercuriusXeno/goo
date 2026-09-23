package com.mercuriusxeno.goo.ability.program;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * The field-effect-with-controller sub-chain as one step (decision
 * field-effect-pattern-branch): the marker stays as a controller while its
 * stacked blobs last. Each tick it ages the strikes in flight, landing each
 * on its entity at {@code strike_tick}; then, off cooldown, it selects the
 * living entities in the radius that every filter keeps and starts a strike
 * on the first one not already struck, spending one charge, with
 * {@code per_stack} charges to a stack. When no stack remains and no strike
 * is in flight it runs the teardown once and finishes. Stacking after the
 * fuse tops the budget off.
 *
 * <p>The strike body runs on a host bound to the struck entity, so it
 * holds entity effect steps; the teardown runs on the marker. The metal
 * trap is {@code radius=3.75 where=[living, not_item, not_sneaking]
 * cooldown=10 per_stack=1 strike_tick=6 strike_ticks=13}, striking with a
 * stalagmite impale and tearing down with smoke and a hiss. The strikes in
 * flight live in the host's {@link FieldEffectState}, which the marker's
 * renderer reads to draw each spike.
 *
 * @param radius      the selection radius in blocks
 * @param where       the filters an entity must pass to be struck
 * @param cooldown    ticks after a strike starts before the next may start
 * @param perStack    charges each stacked blob holds
 * @param strikeTick  the strike age at which the strike body lands, zero to land at once
 * @param strikeTicks how many ticks a strike stays in flight
 * @param strike      the steps run on the struck entity when a strike lands
 * @param teardown    the steps run on the marker once the budget is spent
 */
public record FieldEffectStep(Expr radius, List<EntityFilter> where, Expr cooldown, Expr perStack,
                              Expr strikeTick, Expr strikeTicks, List<Step> strike,
                              List<Step> teardown) implements Step {

    private static final String NAME = "field_effect";
    private static final String FIELD_RADIUS = "radius";
    private static final String FIELD_WHERE = "where";
    private static final String FIELD_COOLDOWN = "cooldown";
    private static final String FIELD_PER_STACK = "per_stack";
    private static final String FIELD_STRIKE_TICK = "strike_tick";
    private static final String FIELD_STRIKE_TICKS = "strike_ticks";
    private static final String FIELD_STRIKE = "strike";
    private static final String FIELD_TEARDOWN = "teardown";

    /**
     * Codec for the step's params. The child list codecs are read lazily
     * because {@link StepTypes} registers this type while building them.
     */
    public static final MapCodec<FieldEffectStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Expr.CODEC.fieldOf(FIELD_RADIUS).forGetter(FieldEffectStep::radius),
            EntityFilter.CODEC.listOf().optionalFieldOf(FIELD_WHERE, List.of()).forGetter(FieldEffectStep::where),
            Expr.CODEC.optionalFieldOf(FIELD_COOLDOWN, Expr.literal(0)).forGetter(FieldEffectStep::cooldown),
            Expr.CODEC.optionalFieldOf(FIELD_PER_STACK, Expr.literal(1)).forGetter(FieldEffectStep::perStack),
            Expr.CODEC.optionalFieldOf(FIELD_STRIKE_TICK, Expr.literal(0)).forGetter(FieldEffectStep::strikeTick),
            Expr.CODEC.optionalFieldOf(FIELD_STRIKE_TICKS, Expr.literal(1)).forGetter(FieldEffectStep::strikeTicks),
            Codec.lazyInitialized(() -> StepTypes.LIST_CODEC).fieldOf(FIELD_STRIKE).forGetter(FieldEffectStep::strike),
            Codec.lazyInitialized(() -> StepTypes.LIST_CODEC).optionalFieldOf(FIELD_TEARDOWN, List.of())
                    .forGetter(FieldEffectStep::teardown)
    ).apply(inst, FieldEffectStep::new));

    /**
     * The registered type.
     */
    public static final StepType<FieldEffectStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<FieldEffectStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        StepHost host = context.host();
        FieldEffectState state = host.fieldEffect();
        state.time(strikeTick.evaluateInt(context), strikeTicks.evaluateInt(context));
        advanceStrikes(host, state);
        if (state.cooldown() > 0) {
            state.setCooldown(state.cooldown() - 1);
        }
        if (host.stackCount() > 0) {
            host.forEachEntityWithin(SelectionShape.SPHERE, radius.evaluate(context), Set.copyOf(where),
                    target -> tryStrike(context, state, target));
        }
        if (host.stackCount() > 0 || !state.strikes().isEmpty()) {
            return false;
        }
        new ProgramBehavior(teardown).tick(host);
        return true;
    }

    /**
     * Ages every strike in flight by one tick, lands each that reaches the
     * landing tick on its entity, and drops each that has run its length.
     *
     * @param host  the marker host
     * @param state the field-effect state
     */
    private void advanceStrikes(StepHost host, FieldEffectState state) {
        List<FieldStrike> kept = new ArrayList<>(state.strikes().size());
        for (FieldStrike strikeInFlight : state.strikes()) {
            FieldStrike aged = strikeInFlight.aged();
            if (aged.age() == state.strikeTick()) {
                host.forEntity(aged.entityId(), this::land);
            }
            if (aged.age() < state.strikeTicks()) {
                kept.add(aged);
            }
        }
        state.setStrikes(kept);
    }

    /**
     * Starts a strike on the selected entity unless the field is cooling
     * down, the entity is already struck or the budget is spent.
     *
     * @param context the marker's tick context
     * @param state   the field-effect state
     * @param target  the host bound to the selected entity
     */
    private void tryStrike(StepContext context, FieldEffectState state, StepHost target) {
        if (state.cooldown() > 0 || state.isStriking(target.targetId()) || context.host().stackCount() <= 0) {
            return;
        }
        spendCharge(context, state);
        Vec3 center = target.targetCenter();
        state.addStrike(new FieldStrike(target.targetId(),
                (float) center.x(), (float) center.y(), (float) center.z(), 0));
        state.setCooldown(cooldown.evaluateInt(context));
        if (state.strikeTick() == 0) {
            land(target);
        }
    }

    /**
     * Spends one charge, decrementing the marker's stack count each time a
     * stack's worth of charges is spent.
     *
     * @param context the marker's tick context
     * @param state   the field-effect state
     */
    private void spendCharge(StepContext context, FieldEffectState state) {
        int spent = state.chargesSpent() + 1;
        if (spent >= perStack.evaluateInt(context)) {
            context.host().decrementStack();
            spent = 0;
        }
        state.setChargesSpent(spent);
    }

    /**
     * Runs the strike body on the struck entity.
     *
     * @param target the host bound to the struck entity
     */
    private void land(StepHost target) {
        new ProgramBehavior(strike).tick(target);
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.of(radius, cooldown, perStack, strikeTick, strikeTicks);
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.STACKS, HostCapability.TICKING, HostCapability.ENTITY_SCAN,
                HostCapability.FIELD_EFFECT);
    }

    @Override
    public Stream<Step> children() {
        return Stream.concat(strike.stream(), teardown.stream());
    }

    @Override
    public Stream<HostedStep> hostedChildren(HostKind host) {
        return Stream.concat(strike.stream().map(child -> new HostedStep(child, HostKind.ENTITY)),
                teardown.stream().map(child -> new HostedStep(child, host)));
    }

    @Override
    public boolean allowsTopOff() {
        return true;
    }
}
