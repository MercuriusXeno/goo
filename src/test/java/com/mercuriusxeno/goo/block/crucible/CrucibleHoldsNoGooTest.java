package com.mercuriusxeno.goo.block.crucible;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests the empty-crucible predicate every spark spawner reads: only a basin
 * whose reservoir and pool together hold nothing shows sparks.
 */
class CrucibleHoldsNoGooTest {

    @ParameterizedTest(name = "reservoir {0}, pool {1} holds no goo: {2}")
    @CsvSource({
        "0, 0, true",
        "1, 0, false",
        "0, 1, false",
        "500, 250, false",
        "1000000000, 0, false",
        "0, 1000000000, false",
    })
    void holdsNoGoo(int reservoirVolume, int poolVolume, boolean expected) {
        assertEquals(expected, CrucibleBasin.holdsNoGoo(reservoirVolume, poolVolume));
    }
}
