package com.mercuriusxeno.goo.block.tap;

import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The tap's drip countdown falls due once per interval of the tap's grade,
 * restarts on demand, retimes into a new grade, and survives a save and load
 * at the tick it stood on.
 */
class TapDripCountdownTest {

    private static final int INTERVAL = 40;
    private static final int TICKS_RUN = 13;
    private static final int SLOWEST_INTERVAL_TICKS = 64;
    private static final int TICKS_MEASURED = 256;
    private static final Map<TapDripGrade, Integer> EXPECTED_LOSS_OVER_256_TICKS = Map.of(
            TapDripGrade.ONE_PER_64_TICKS, 4,
            TapDripGrade.ONE_PER_16_TICKS, 16,
            TapDripGrade.ONE_PER_4_TICKS, 64,
            TapDripGrade.ONE_PER_TICK, 256,
            TapDripGrade.FOUR_PER_TICK, 1024);

    private static TapDripCountdown runFor(int ticks) {
        TapDripCountdown countdown = new TapDripCountdown(INTERVAL);
        for (int i = 0; i < ticks; i++) {
            countdown.tick();
        }
        return countdown;
    }

    @Test
    void fallsDueOnceEveryInterval() {
        TapDripCountdown countdown = new TapDripCountdown(INTERVAL);
        int due = 0;
        for (int i = 1; i <= INTERVAL * 3; i++) {
            if (countdown.tick()) {
                due++;
                assertEquals(0, i % INTERVAL, "a drip falls due on an interval boundary, at tick " + i);
            }
        }
        assertEquals(3, due);
    }

    @ParameterizedTest
    @EnumSource(TapDripGrade.class)
    void eachGradeLosesItsRateOver256Ticks(TapDripGrade grade) {
        TapDripCountdown countdown = new TapDripCountdown(grade.intervalTicks());
        int lostMb = 0;
        for (int i = 0; i < TICKS_MEASURED; i++) {
            if (countdown.tick()) {
                lostMb += grade.dripVolume();
            }
        }
        assertEquals(EXPECTED_LOSS_OVER_256_TICKS.get(grade), lostMb);
    }

    @Test
    void retimeToAFasterGradeClampsTheTicksLeft() {
        TapDripCountdown countdown = new TapDripCountdown(SLOWEST_INTERVAL_TICKS);
        countdown.retime(TapDripGrade.ONE_PER_4_TICKS.intervalTicks());

        assertEquals(TapDripGrade.ONE_PER_4_TICKS.intervalTicks(), countdown.ticksLeft());
        int due = 0;
        for (int i = 0; i < TapDripGrade.ONE_PER_4_TICKS.intervalTicks() * 2; i++) {
            if (countdown.tick()) {
                due++;
            }
        }
        assertEquals(2, due);
    }

    @Test
    void retimeToASlowerGradeKeepsTheTicksLeft() {
        TapDripCountdown countdown = runFor(TICKS_RUN);
        countdown.retime(SLOWEST_INTERVAL_TICKS);

        assertEquals(INTERVAL - TICKS_RUN, countdown.ticksLeft());
    }

    @Test
    void restartStartsAFullInterval() {
        TapDripCountdown countdown = runFor(TICKS_RUN);
        countdown.restart();

        assertEquals(INTERVAL, countdown.ticksLeft());
        for (int i = 1; i < INTERVAL; i++) {
            assertFalse(countdown.tick());
        }
        assertTrue(countdown.tick());
    }

    @Test
    void saveAndLoadKeepTheTicksLeft() {
        TapDripCountdown saved = runFor(TICKS_RUN);
        ValueOutput output = mock(ValueOutput.class);
        saved.save(output);
        ArgumentCaptor<Integer> written = ArgumentCaptor.forClass(Integer.class);
        verify(output).putInt(eq(TapDripCountdown.TAG_DRIP_COUNTDOWN), written.capture());

        ValueInput input = mock(ValueInput.class);
        when(input.getIntOr(eq(TapDripCountdown.TAG_DRIP_COUNTDOWN), anyInt())).thenReturn(written.getValue());
        TapDripCountdown loaded = new TapDripCountdown(INTERVAL);
        loaded.load(input);

        assertEquals(INTERVAL - TICKS_RUN, loaded.ticksLeft());
        assertEquals(saved.ticksLeft(), loaded.ticksLeft());
    }

    @Test
    void loadWithNoSavedCountdownStartsAFullInterval() {
        ValueInput input = mock(ValueInput.class);
        when(input.getIntOr(eq(TapDripCountdown.TAG_DRIP_COUNTDOWN), anyInt()))
                .thenAnswer(call -> call.getArgument(1));
        TapDripCountdown loaded = runFor(TICKS_RUN);
        loaded.load(input);

        assertEquals(INTERVAL, loaded.ticksLeft());
    }

    @Test
    void loadClampsAnOutOfRangeCountdownIntoOneInterval() {
        ValueInput input = mock(ValueInput.class);
        TapDripCountdown loaded = new TapDripCountdown(INTERVAL);

        when(input.getIntOr(eq(TapDripCountdown.TAG_DRIP_COUNTDOWN), anyInt())).thenReturn(INTERVAL * 5);
        loaded.load(input);
        assertEquals(INTERVAL, loaded.ticksLeft());

        when(input.getIntOr(eq(TapDripCountdown.TAG_DRIP_COUNTDOWN), anyInt())).thenReturn(-7);
        loaded.load(input);
        assertEquals(1, loaded.ticksLeft());
    }
}
