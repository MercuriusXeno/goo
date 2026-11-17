package com.mercuriusxeno.goo.ability;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The knives a Knives hold has formed at a held tick count: none before the
 * first stage's twelve ticks, then 1, 2, 4, 7 and 12 at each stage, and a
 * sixth stage adds 8 (decisions knives-form-in-fibonacci-stages-to-twelve,
 * knives-stage-takes-twelve-ticks-with-no-cooldown).
 */
class KnifeStagesTest {

    private static final int STAGE_TICKS = 12;

    @ParameterizedTest
    @CsvSource({"0, 5, 0", "11, 5, 0", "12, 5, 1", "24, 5, 2", "36, 5, 4", "48, 5, 7", "60, 5, 12",
        "59, 5, 7", "200, 5, 12", "72, 6, 20"})
    void knivesFormInFibonacciStagesOfTwelveTicks(int heldTicks, int stages, int formed) {
        assertEquals(formed, KnifeStages.formedAt(heldTicks, stages, STAGE_TICKS));
    }
}
