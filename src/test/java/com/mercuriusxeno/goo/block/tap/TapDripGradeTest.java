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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The tap's drip grade steps through five rates and off on each valve click,
 * and survives a save and load.
 */
class TapDripGradeTest {

    private static final int VALVE_CLICKS = 6;

    @Test
    void sixClicksFromOffStepThroughFiveGradesAndBackToOff() {
        List<Optional<TapDripGrade>> seen = new ArrayList<>();
        Optional<TapDripGrade> grade = Optional.empty();
        for (int click = 0; click < VALVE_CLICKS; click++) {
            grade = TapDripGrade.afterValveClick(grade);
            seen.add(grade);
        }

        assertEquals(List.of(Optional.of(TapDripGrade.ONE_PER_256_TICKS), Optional.of(TapDripGrade.ONE_PER_64_TICKS),
                Optional.of(TapDripGrade.ONE_PER_16_TICKS), Optional.of(TapDripGrade.ONE_PER_4_TICKS),
                Optional.of(TapDripGrade.ONE_PER_TICK), Optional.<TapDripGrade>empty()), seen);
    }

    @Test
    void gradesStepInFourfoldIntervals() {
        assertEquals(List.of(256, 64, 16, 4, 1),
                List.of(TapDripGrade.values()).stream().map(TapDripGrade::intervalTicks).toList());
    }

    @ParameterizedTest
    @EnumSource(TapDripGrade.class)
    void saveAndLoadKeepTheGrade(TapDripGrade grade) {
        ValueOutput output = mock(ValueOutput.class);
        grade.save(output);
        ArgumentCaptor<Integer> written = ArgumentCaptor.forClass(Integer.class);
        verify(output).putInt(eq(TapDripGrade.TAG_DRIP_GRADE), written.capture());

        ValueInput input = mock(ValueInput.class);
        when(input.getIntOr(eq(TapDripGrade.TAG_DRIP_GRADE), anyInt())).thenReturn(written.getValue());

        assertEquals(grade, TapDripGrade.load(input));
    }

    @Test
    void loadWithNoSavedGradeReadsTheSlowest() {
        ValueInput input = mock(ValueInput.class);
        when(input.getIntOr(eq(TapDripGrade.TAG_DRIP_GRADE), anyInt())).thenAnswer(call -> call.getArgument(1));

        assertEquals(TapDripGrade.SLOWEST, TapDripGrade.load(input));
    }

    @Test
    void loadOfAnUnknownIntervalReadsTheSlowest() {
        ValueInput input = mock(ValueInput.class);
        when(input.getIntOr(eq(TapDripGrade.TAG_DRIP_GRADE), anyInt())).thenReturn(40);

        assertEquals(TapDripGrade.SLOWEST, TapDripGrade.load(input));
    }
}
