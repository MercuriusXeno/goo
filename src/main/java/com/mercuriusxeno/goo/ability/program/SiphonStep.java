package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.data.GooValue;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Unmake's channel: each held tick keeps the soup held before the player and,
 * when it is ready, starts the next block on the face under the cursor
 * siphoning into it, the outer ring first and the aimed block last. A block burns its fuel as it
 * starts and goes until it is done; one with no goo value, or one the player
 * cannot pay for, stands.
 * decision unmake-waves-dissolve-by-crucible-cost
 *
 * @param radius how far the face's square reaches from the aimed block, 1 for a 3x3
 * @param speed  how much faster than its pace the soup drinks, 1 at its pace
 */
public record SiphonStep(Expr radius, Expr speed) implements Step {

    private static final String NAME = "siphon";
    private static final String FIELD_RADIUS = "radius";
    private static final String FIELD_SPEED = "speed";

    /**
     * Codec for the step's params; a siphon naming neither drinks a 3x3 at its pace.
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
        host.holdSoup();
        if (!host.readyToSiphon()) {
            return true;
        }
        double pace = speed.evaluate(context);
        for (BlockPos pos : host.siphonFace(Math.max(0, (int) Math.round(radius.evaluate(context))))) {
            GooValue value = host.siphonValue(pos);
            if (value == null || value.isEmpty()) {
                continue;
            }
            if (host.burnUnstable(SiphonRule.fuelFor(value.totalGoo(), host.meltExponent(), host.ticksPerMb()))) {
                host.siphon(pos, value.toGooContents(), SiphonRule.siphonTicks(pace), SiphonRule.startInterval(pace));
            }
            return true;
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
