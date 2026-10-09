package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.hex.MonsterStirring;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Stirs monsters around the host for as long as it stands: each time its
 * countdown runs out it attempts one natural monster spawn within the
 * radius, and the interval to the next attempt shrinks by the factor after a
 * failure, down to the floor, and returns to the start after a spawn. The
 * step never finishes. Hex's agitator prism is
 * {@code agitate radius=8 start_interval=400 shrink=0.75 min_interval=40}.
 * agitator-prism-quickens-until-a-spawn
 *
 * @param radius        how far from the host, on each axis, a monster may be placed
 * @param startInterval the ticks between attempts at the start and after each spawn
 * @param shrink        the share of the interval a failed attempt keeps, 0 to 1
 * @param minInterval   the shortest interval
 */
public record AgitateStep(int radius, int startInterval, double shrink, int minInterval) implements Step {

    private static final String NAME = "agitate";
    private static final String FIELD_RADIUS = "radius";
    private static final String FIELD_START_INTERVAL = "start_interval";
    private static final String FIELD_SHRINK = "shrink";
    private static final String FIELD_MIN_INTERVAL = "min_interval";

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<AgitateStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Codec.INT.fieldOf(FIELD_RADIUS).forGetter(AgitateStep::radius),
            Codec.INT.fieldOf(FIELD_START_INTERVAL).forGetter(AgitateStep::startInterval),
            Codec.DOUBLE.fieldOf(FIELD_SHRINK).forGetter(AgitateStep::shrink),
            Codec.INT.fieldOf(FIELD_MIN_INTERVAL).forGetter(AgitateStep::minInterval)
    ).apply(inst, AgitateStep::new));

    /**
     * The registered type.
     */
    public static final StepType<AgitateStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<AgitateStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        AgitateHost host = context.hostAs(AgitateHost.class);
        AgitationState state = host.agitation();
        state.startIfIdle(startInterval);
        if (state.tickDown()) {
            boolean spawned = MonsterStirring.attempt(host.level(), host.position(), radius);
            state.restart(AgitationState.nextInterval(state.interval(), spawned, startInterval, shrink, minInterval));
        }
        return false;
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.empty();
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.TICKING, HostCapability.AGITATE);
    }
}
