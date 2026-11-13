package com.mercuriusxeno.goo.ability.program;

import net.minecraft.world.level.storage.ValueOutput;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;
import java.util.Set;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;

/**
 * A field trap's pickup is worth a whole throw times the charges left over
 * the charges bought (decision recollect-returns-charges-left), its density
 * is the charges left over the peak, its radius rises over the expand and
 * falls over the contract, and its saved state holds its seven run-state fields.
 */
class FieldEffectStateTest {

    private static final int WHOLE = 1000;
    private static final int BOUGHT = 8;
    private static final int ANIMATION_TICKS = 10;

    @ParameterizedTest
    @CsvSource({"4, 1, 250", "8, 8, 1000", "8, 3, 375", "4, 0, 0"})
    void aTrapIsWorthTheChargesItStillHolds(int bought, int left, int worth) {
        FieldEffectState state = new FieldEffectState();
        state.recordCharges(bought);
        state.recordCharges(left);
        assertEquals(worth, state.worthLeft(WHOLE));
    }

    @Test
    void theDensityIsTheChargesLeftOverThePeakBought() {
        FieldEffectState state = new FieldEffectState();
        state.recordCharges(BOUGHT);
        state.recordCharges(BOUGHT / 2);
        assertEquals(0.5f, state.density());
    }

    @Test
    void theRadiusRisesWithTheFieldsTicksOverItsExpand() {
        FieldEffectState state = new FieldEffectState();
        for (int tick = 0; tick < ANIMATION_TICKS / 2; tick++) {
            state.countTick();
        }
        assertEquals(0.5f, state.radiusFraction(ANIMATION_TICKS, ANIMATION_TICKS));
    }

    @Test
    void theRadiusFallsWithItsTeardownTicksOverItsContract() {
        FieldEffectState state = new FieldEffectState();
        state.setTeardownTicks(ANIMATION_TICKS / 2);
        assertEquals(0.5f, state.radiusFraction(ANIMATION_TICKS, ANIMATION_TICKS));
    }

    @Test
    void aMarkerWithNoTrapIsWorthAWholeThrow() {
        assertEquals(WHOLE, new FieldEffectState().worthLeft(WHOLE));
    }

    @Test
    void theSavedStateHoldsItsSevenRunStateFields() {
        ValueOutput output = mock(ValueOutput.class);

        new FieldEffectState().save(output);

        Set<String> savedTags = mockingDetails(output).getInvocations().stream()
                .map(invocation -> invocation.<String>getArgument(0))
                .collect(Collectors.toSet());
        assertEquals(Set.of("FieldStrikes", "FieldCooldown", "FieldChargesSpent", "FieldTicks",
                "FieldTeardownTicks", "FieldChargesLeft", "FieldMaxCharges"), savedTags);
    }
}
