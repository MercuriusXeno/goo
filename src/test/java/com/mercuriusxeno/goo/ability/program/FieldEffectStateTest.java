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
 * the charges bought (decision recollect-returns-charges-left), and its
 * saved state holds its seven run-state fields.
 */
class FieldEffectStateTest {

    private static final int WHOLE = 1000;

    @ParameterizedTest
    @CsvSource({"4, 1, 250", "8, 8, 1000", "8, 3, 375", "4, 0, 0"})
    void aTrapIsWorthTheChargesItStillHolds(int bought, int left, int worth) {
        FieldEffectState state = new FieldEffectState();
        state.recordCharges(bought);
        state.recordCharges(left);
        assertEquals(worth, state.worthLeft(WHOLE));
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
