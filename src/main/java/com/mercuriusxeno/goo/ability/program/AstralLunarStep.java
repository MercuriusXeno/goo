package com.mercuriusxeno.goo.ability.program;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Lunar, run each held tick of the channel: the first held tick fixes the caster's gaze on the moon or the
 * blue planet their look holds, holding them still while it swells to fill their view, and each tick after
 * keeps the pull; held through, it carries them and takes its cost, and let go early, it releases them and takes
 * nothing. It places nothing. Lunar is {@code astral_lunar fixation=100 cost=1000}, five seconds of
 * fixation, the time the trip takes to dim the sky, darken the land, swell the moon and flood the view white.
 * decision gate-fixates-your-view-on-the-moon-until-it-takes-you
 * decision gate-on-the-blue-star-returns-you-exactly-where-you-left
 * decision holding-right-click-pulls-the-moon-and-early-release-cancels
 *
 * @param fixation the held ticks the fixation runs before Lunar carries the caster
 * @param cost     the astral Lunar costs as the moon or the planet takes the caster (mB)
 */
public record AstralLunarStep(Expr fixation, Expr cost) implements Step {

    private static final String NAME = "astral_lunar";
    private static final String FIELD_FIXATION = "fixation";
    private static final String FIELD_COST = "cost";

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<AstralLunarStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Expr.CODEC.fieldOf(FIELD_FIXATION).forGetter(AstralLunarStep::fixation),
            Expr.CODEC.fieldOf(FIELD_COST).forGetter(AstralLunarStep::cost)
    ).apply(inst, AstralLunarStep::new));

    /**
     * The registered type.
     */
    public static final StepType<AstralLunarStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<AstralLunarStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        context.hostAs(AstralLunarHost.class).fixateOnTheSky(fixation.evaluateInt(context), cost.evaluateInt(context));
        return true;
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.of(fixation, cost);
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.ASTRAL_LUNAR);
    }
}
