package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.pulse.RedstoneBeat;
import com.mojang.serialization.MapCodec;
import net.minecraft.resources.Identifier;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Metronome's combo step, running for as long as the prism stands: the
 * prism gives full redstone power for one tick every interval its last two
 * signals set, counted from the last, and none between; until it has heard
 * two signals it gives none: {@code metronome}.
 * metronome-prism-pulses-at-the-learned-rate
 */
public record MetronomeStep() implements Step {

    private static final String NAME = "metronome";

    /**
     * Codec for the step, which takes no params.
     */
    public static final MapCodec<MetronomeStep> CODEC = MapCodec.unit(MetronomeStep::new);

    /**
     * The registered type.
     */
    public static final StepType<MetronomeStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<MetronomeStep> type() {
        return TYPE;
    }

    /**
     * The tick each beat plays, a soft clock tick so the rhythm carries
     * across a room (operator UAT: the metronome struggled with conveyance).
     */
    static final SoundCue BEAT_TICK = new SoundCue(Identifier.withDefaultNamespace("block.note_block.hat"),
            SoundKind.BLOCKS, 0.5f, 1.6f);

    @Override
    public boolean tick(StepContext context) {
        BeatHost beatHost = context.hostAs(BeatHost.class);
        RedstoneBeat beat = beatHost.beat();
        boolean pulses = beat.pulsesAt(beatHost.gameTime());
        context.hostAs(PowerEmitHost.class).emitPower(pulses);
        if (pulses) {
            context.playSound(BEAT_TICK, 0);
        }
        return false;
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.empty();
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.TICKING, HostCapability.EMIT_POWER, HostCapability.BEAT);
    }
}
