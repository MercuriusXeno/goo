package com.mercuriusxeno.goo.ability.program;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Pulser's block step, run each held tick of the stream: every JSON-named
 * count of held ticks, every lever, button, door, trapdoor or fence gate in
 * the cone along the aim toggles, for as long as right click holds, with no
 * cap on how often each toggles:
 * {@code pulser_toggle every=4}.
 * pulser-toggles-rapidly-while-held
 *
 * @param every the held ticks between toggles, evaluated when the step runs
 */
public record PulserToggleStep(Expr every) implements Step {

    private static final String NAME = "pulser_toggle";
    private static final String FIELD_EVERY = "every";

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<PulserToggleStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Expr.CODEC.fieldOf(FIELD_EVERY).forGetter(PulserToggleStep::every)
    ).apply(inst, PulserToggleStep::new));

    /**
     * The registered type.
     */
    public static final StepType<PulserToggleStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<PulserToggleStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        ChannelHost host = context.hostAs(ChannelHost.class);
        host.channelAim()
                .filter(aim -> togglesOn(aim.held(), Math.round(every.evaluateFloat(context))))
                .ifPresent(aim -> host.toggleEachDevice(
                        CalcifyStep.blocksInCone(host.eye(), aim.aimPoint(), aim.coneDegrees())));
        return true;
    }

    /**
     * Whether the devices toggle on a tick of the hold: on the hold's first
     * tick, then once each period after.
     *
     * @param held  the hold's tick count, 1 on its first tick
     * @param every the held ticks between toggles; below 1 counts as 1
     * @return true on a toggling tick
     */
    static boolean togglesOn(int held, int every) {
        return held >= 1 && (held - 1) % Math.max(1, every) == 0;
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.of(every);
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.CHANNEL);
    }
}
