package com.mercuriusxeno.goo.ability;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The knives a Knives hold has formed at a held tick count: none before the
 * first stage's twelve ticks, then 1, 2, 4, 7 and 12 at each stage, and a
 * sixth stage adds 8; a stage after a throw, the knives left round up to the
 * lowest stage holding them and fill to the next stage's total, never past
 * the last (decisions knives-form-in-fibonacci-stages-to-twelve,
 * knives-stage-takes-twelve-ticks-with-no-cooldown, knives-refill-from-the-stage-the-fan-rounds-up-to).
 */
class KnifeStagesTest {

    private static final int STAGE_TICKS = 12;

    @ParameterizedTest
    @CsvSource({"0, 5, 0", "11, 5, 0", "12, 5, 1", "24, 5, 2", "36, 5, 4", "48, 5, 7", "60, 5, 12",
        "59, 5, 7", "200, 5, 12", "72, 6, 20"})
    void knivesFormInFibonacciStagesOfTwelveTicks(int heldTicks, int stages, int formed) {
        assertEquals(formed, KnifeStages.formedAt(heldTicks, stages, STAGE_TICKS));
    }

    /** knives-refill-from-the-stage-the-fan-rounds-up-to */
    @ParameterizedTest
    @CsvSource({"0, 1", "1, 2", "2, 4", "3, 7", "4, 7", "5, 12", "7, 12", "8, 12", "12, 12"})
    void aStageAfterAThrowTheKnivesLeftRoundUpToTheirStageAndFillToTheNext(int left, int refilled) {
        assertEquals(refilled, KnifeStages.refilledFrom(left, 5));
    }
}
