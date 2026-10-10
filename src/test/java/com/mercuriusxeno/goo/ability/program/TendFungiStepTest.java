package com.mercuriusxeno.goo.ability.program;

import org.junit.jupiter.api.Test;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A held fungal bud ages at nether wart's pace: one roll in ten ages it.
 * mycosis-grows-and-reaps-nether-wart
 */
class TendFungiStepTest {

    private static final int WART_ODDS = 10;

    @Test
    void aBudAgesOnOneRollInTen() {
        assertEquals(WART_ODDS, TendFungiStep.BUD_ODDS);
        assertEquals(1, IntStream.range(0, TendFungiStep.BUD_ODDS).filter(TendFungiStep::budAges).count());
    }
}
