package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.pulse.RedstoneBeat;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Each beat the metronome gives plays a clock tick with its pulse of power,
 * and the ticks between beats play nothing
 * (decision metronome-prism-pulses-at-the-learned-rate).
 */
class MetronomeStepTest {

    /** Signals heard at ticks 0 and 20: a beat every 20 ticks after the last. */
    private static final RedstoneBeat EVERY_TWENTY = new RedstoneBeat(0, 20, false);

    /** A host with every capability the metronome step needs. */
    interface MetronomeHost extends BeatHost, PowerEmitHost {
    }

    private static MetronomeHost hostAt(long gameTime) {
        MetronomeHost host = mock(MetronomeHost.class);
        when(host.beat()).thenReturn(EVERY_TWENTY);
        when(host.gameTime()).thenReturn(gameTime);
        return host;
    }

    @Test
    void aBeatTicksWithItsPulse() {
        MetronomeHost host = hostAt(40);
        new MetronomeStep().tick(new StepContext(host, 0, 0));
        verify(host).emitPower(true);
        verify(host).playSound(MetronomeStep.BEAT_TICK);
    }

    @Test
    void theTicksBetweenBeatsAreSilent() {
        MetronomeHost host = hostAt(41);
        new MetronomeStep().tick(new StepContext(host, 0, 0));
        verify(host).emitPower(false);
        verify(host, never()).playSound(any());
    }
}
