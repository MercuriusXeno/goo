package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.typhoon.Breath;
import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.LivingEntity;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Runs one held tick of typhoon Pneuma on the host's target and finishes: the
 * target's air rises by the refill, the air past its meter banks in the
 * reserve up to the cap, and the airless immunity window opens. Run every held
 * tick of the channel, the window closes its length after the hold lets go.
 * Pneuma is {@code pneuma refill=30 reserve_cap=3000 immunity=60}.
 * pneuma-overfills-the-air-meter-with-a-brief-airless-immunity
 *
 * @param refill     the air one held tick refills, in ticks of breath
 * @param reserveCap the most air the reserve banks, in ticks of breath
 * @param immunity   the ticks the airless immunity stands after a held tick
 */
public record PneumaStep(Expr refill, Expr reserveCap, Expr immunity) implements Step {

    private static final String NAME = "pneuma";
    private static final String FIELD_REFILL = "refill";
    private static final String FIELD_RESERVE_CAP = "reserve_cap";
    private static final String FIELD_IMMUNITY = "immunity";

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<PneumaStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Expr.CODEC.fieldOf(FIELD_REFILL).forGetter(PneumaStep::refill),
            Expr.CODEC.fieldOf(FIELD_RESERVE_CAP).forGetter(PneumaStep::reserveCap),
            Expr.CODEC.fieldOf(FIELD_IMMUNITY).forGetter(PneumaStep::immunity)
    ).apply(inst, PneumaStep::new));

    /**
     * The registered type.
     */
    public static final StepType<PneumaStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<PneumaStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        LivingEntity target = context.hostAs(TargetHost.class).target();
        Breath.Refilled refilled = target.getData(GooAttachments.BREATH).refilled(target.getAirSupply(),
                target.getMaxAirSupply(), refill.evaluateInt(context), reserveCap.evaluateInt(context),
                immunity.evaluateInt(context), target.level().getGameTime());
        target.setAirSupply(refilled.air());
        target.setData(GooAttachments.BREATH, refilled.breath());
        return true;
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.of(refill, reserveCap, immunity);
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.TARGET);
    }
}
