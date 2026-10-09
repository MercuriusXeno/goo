package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.data.GooValue;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Unmake's channel: each held tick keeps the drink held and starts every
 * block in the cone before the eye streaming into it, all together. A block
 * burns its fuel as it starts and streams in over the unstable crucible's
 * own time for it; one with no goo value, or one the player cannot pay for,
 * stands and holds up none of the others.
 * decision unmake-waves-dissolve-by-crucible-cost
 *
 * @param radius how far the cone's square reaches from its axis at mid range, 1 for a 3x3
 * @param speed  how much faster than the crucible the drink goes, 1 at its pace
 */
public record SiphonStep(Expr radius, Expr speed) implements Step {

    private static final String NAME = "siphon";
    private static final String FIELD_RADIUS = "radius";
    private static final String FIELD_SPEED = "speed";

    /**
     * Codec for the step's params; a siphon naming neither drinks a 3x3 cone at the crucible's pace.
     */
    public static final MapCodec<SiphonStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Expr.CODEC.optionalFieldOf(FIELD_RADIUS, Expr.literal(1)).forGetter(SiphonStep::radius),
            Expr.CODEC.optionalFieldOf(FIELD_SPEED, Expr.literal(1)).forGetter(SiphonStep::speed)
    ).apply(inst, SiphonStep::new));

    /**
     * The registered type.
     */
    public static final StepType<SiphonStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<SiphonStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        SiphonHost host = context.hostAs(SiphonHost.class);
        host.holdDrink();
        double speed = this.speed.evaluate(context);
        for (BlockPos pos : host.siphonCone(Math.max(0, (int) Math.round(radius.evaluate(context))))) {
            GooValue value = host.siphonValue(pos);
            if (value == null || value.isEmpty()) {
                continue;
            }
            if (host.burnUnstable(SiphonRule.fuelFor(value.totalGoo(), host.meltExponent(), host.ticksPerMb()))) {
                host.siphon(pos, value.toGooContents(),
                        SiphonRule.siphonTicks(value.totalGoo(), host.meltExponent(), speed));
            }
        }
        return true;
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.of(radius, speed);
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.SIPHON);
    }
}
