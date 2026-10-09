package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.network.EntityVisuals;
import com.mercuriusxeno.goo.network.LeechPayload;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.LivingEntity;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Runs its strike on the host's target, then heals the caster by the
 * fraction of the health the strike took, the life flowing from the target
 * to the caster as Lifetap's wisps, and finishes. Drain streams
 * {@code leech fraction=0.5 strike=[damage amount=2 source=attack]} over
 * every mob in its cone.
 * drain-field-heals-with-the-lifetap-visuals
 *
 * @param fraction the share of the health taken that heals the caster, evaluated when the step runs
 * @param strike   the steps that hurt the target, instant ones
 */
public record LeechStep(Expr fraction, List<Step> strike) implements Step {

    private static final String NAME = "leech";
    private static final String FIELD_FRACTION = "fraction";
    private static final String FIELD_STRIKE = "strike";

    /**
     * Codec for the step's params. The list codec is read lazily because
     * {@link StepTypes} registers this type while building it.
     */
    public static final MapCodec<LeechStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Expr.CODEC.fieldOf(FIELD_FRACTION).forGetter(LeechStep::fraction),
            Codec.lazyInitialized(() -> StepTypes.LIST_CODEC).fieldOf(FIELD_STRIKE).forGetter(LeechStep::strike)
    ).apply(inst, LeechStep::new));

    /**
     * The registered type.
     */
    public static final StepType<LeechStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<LeechStep> type() {
        return TYPE;
    }

    /**
     * The health a leech heals: the fraction of the health the strike took.
     *
     * @param before   the target's health before the strike
     * @param after    the target's health after it
     * @param leech    the share of the health taken that heals
     * @return the health healed, never below zero
     */
    static float healOf(float before, float after, float leech) {
        return Math.max(0f, before - after) * leech;
    }

    @Override
    public boolean tick(StepContext context) {
        TargetHost host = context.hostAs(TargetHost.class);
        LivingEntity target = host.target();
        float before = target.getHealth();
        new ProgramBehavior(strike).tick(context.host());
        float heal = healOf(before, Math.max(0f, target.getHealth()), fraction.evaluateFloat(context));
        if (heal > 0f && host.thrower() instanceof LivingEntity caster && caster != target) {
            caster.heal(heal);
            EntityVisuals.sendToWatchers(target, new LeechPayload(target.getId(), caster.getId(), heal, false));
        }
        return true;
    }

    @Override
    public Stream<Step> children() {
        return strike.stream();
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.of(fraction);
    }

    @Override
    public Set<HostCapability> requires() {
        Set<HostCapability> needs = new HashSet<>(Set.of(HostCapability.TARGET));
        children().forEach(child -> needs.addAll(child.requires()));
        return needs;
    }
}
