package com.mercuriusxeno.goo.block.tap;

import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The tap's drip grade steps forward through five rates and off on each
 * valve click and back on each sneaking click,
 * drips no slower than one per 64 ticks, and survives a save and load,
 * including a save made before 1:4 existed.
 */
class TapDripGradeTest {

    private static final int VALVE_CLICKS = 6;
    private static final int DROPPED_SLOWEST_INTERVAL = 256;
    private static final int SLOWEST_INTERVAL = 64;

    private static ValueInput legacySave(int intervalTicks) {
        ValueInput input = mock(ValueInput.class);
        when(input.getString(TapDripGrade.TAG_DRIP_RATE)).thenReturn(Optional.empty());
        when(input.getIntOr(eq(TapDripGrade.TAG_LEGACY_DRIP_GRADE), anyInt())).thenReturn(intervalTicks);
        return input;
    }

    @Test
    void sixClicksFromOffStepThroughFiveGradesAndBackToOff() {
        List<Optional<TapDripGrade>> seen = new ArrayList<>();
        Optional<TapDripGrade> grade = Optional.empty();
        for (int click = 0; click < VALVE_CLICKS; click++) {
            grade = TapDripGrade.afterValveClick(grade);
            seen.add(grade);
        }

        assertEquals(List.of(Optional.of(TapDripGrade.ONE_PER_64_TICKS), Optional.of(TapDripGrade.ONE_PER_16_TICKS),
                Optional.of(TapDripGrade.ONE_PER_4_TICKS), Optional.of(TapDripGrade.ONE_PER_TICK),
                Optional.of(TapDripGrade.FOUR_PER_TICK), Optional.<TapDripGrade>empty()), seen);
    }

    @Test
    void sneakClicksFromOneToFourStepBackThroughEachGradeToOffAndStayOff() {
        List<Optional<TapDripGrade>> seen = new ArrayList<>();
        Optional<TapDripGrade> grade = Optional.of(TapDripGrade.FOUR_PER_TICK);
        for (int click = 0; click < VALVE_CLICKS; click++) {
            grade = TapDripGrade.afterSneakValveClick(grade);
            seen.add(grade);
        }

        assertEquals(List.of(Optional.of(TapDripGrade.ONE_PER_TICK), Optional.of(TapDripGrade.ONE_PER_4_TICKS),
                Optional.of(TapDripGrade.ONE_PER_16_TICKS), Optional.of(TapDripGrade.ONE_PER_64_TICKS),
                Optional.<TapDripGrade>empty(), Optional.<TapDripGrade>empty()), seen);
    }

    @Test
    void gradesStepInFourfoldRates() {
        assertEquals(List.of(4, 16, 64, 256, 1024), List.of(TapDripGrade.values()).stream()
                .map(grade -> DROPPED_SLOWEST_INTERVAL / grade.intervalTicks() * grade.dripVolume()).toList());
    }

    @Test
    void noGradeDripsSlowerThanOnePerSixtyFourTicks() {
        assertTrue(List.of(TapDripGrade.values()).stream()
                .allMatch(grade -> grade.intervalTicks() <= SLOWEST_INTERVAL));
        assertEquals(TapDripGrade.ONE_PER_64_TICKS, TapDripGrade.SLOWEST);
    }

    @ParameterizedTest
    @EnumSource(TapDripGrade.class)
    void saveAndLoadKeepTheGrade(TapDripGrade grade) {
        ValueOutput output = mock(ValueOutput.class);
        grade.save(output);
        ArgumentCaptor<String> written = ArgumentCaptor.forClass(String.class);
        verify(output).putString(eq(TapDripGrade.TAG_DRIP_RATE), written.capture());

        ValueInput input = mock(ValueInput.class);
        when(input.getString(TapDripGrade.TAG_DRIP_RATE)).thenReturn(Optional.of(written.getValue()));

        assertEquals(grade, TapDripGrade.load(input));
    }

    @Test
    void aTapSavedAtTheDroppedTwoFiftySixLoadsAtSixtyFour() {
        assertEquals(TapDripGrade.ONE_PER_64_TICKS, TapDripGrade.load(legacySave(DROPPED_SLOWEST_INTERVAL)));
    }

    @Test
    void aTapSavedAtOneToOneBeforeOneToFourExistedLoadsAtOneToOne() {
        assertEquals(TapDripGrade.ONE_PER_TICK, TapDripGrade.load(legacySave(1)));
    }

    @Test
    void loadWithNoSavedGradeReadsTheSlowest() {
        ValueInput input = mock(ValueInput.class);
        when(input.getString(TapDripGrade.TAG_DRIP_RATE)).thenReturn(Optional.empty());
        when(input.getIntOr(eq(TapDripGrade.TAG_LEGACY_DRIP_GRADE), anyInt()))
                .thenAnswer(call -> call.getArgument(1));

        assertEquals(TapDripGrade.SLOWEST, TapDripGrade.load(input));
    }

    @Test
    void loadOfAnUnknownNameReadsTheSlowest() {
        ValueInput input = mock(ValueInput.class);
        when(input.getString(TapDripGrade.TAG_DRIP_RATE)).thenReturn(Optional.of("ONE_PER_256_TICKS"));

        assertEquals(TapDripGrade.SLOWEST, TapDripGrade.load(input));
    }
}
