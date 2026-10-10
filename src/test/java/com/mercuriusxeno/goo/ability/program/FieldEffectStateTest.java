package com.mercuriusxeno.goo.ability.program;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A field trap's pickup is worth a whole throw times the charges left over
 * the charges bought (decision recollect-returns-charges-left).
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
}
