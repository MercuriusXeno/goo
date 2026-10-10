package com.mercuriusxeno.goo.client.ber.style;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A timekeeper prism's shell reads its bank: an empty bank stands dim and
 * still-paced, a fuller one marches faster and glows brighter, up to the
 * standing ceiling under the spending race
 * (decision timekeeper-prism-banks-ticks-forward-only).
 */
class TimekeeperPrismStyleTest {

    private static final long A_DAY = 24_000L;

    @Test
    void anEmptyBankStandsDimAndSlow() {
        assertEquals(0, TimekeeperPrismStyle.standingPace(0));
        assertEquals(TimekeeperPrismStyle.EMPTY_GLOW, TimekeeperPrismStyle.glow(0), 1e-6f);
    }

    @Test
    void aFullerBankMarchesFasterAndGlowsBrighter() {
        assertTrue(TimekeeperPrismStyle.standingPace(A_DAY) > TimekeeperPrismStyle.standingPace(A_DAY / 100));
        assertTrue(TimekeeperPrismStyle.glow(A_DAY) > TimekeeperPrismStyle.glow(A_DAY / 100));
    }

    @Test
    void aStandingBankNeverRacesLikeSpending() {
        assertEquals(TimekeeperPrismStyle.MOST_STANDING_PACE, TimekeeperPrismStyle.standingPace(Long.MAX_VALUE / 2));
        assertTrue(TimekeeperPrismStyle.MOST_STANDING_PACE < TimekeeperPrismStyle.SPENDING_PACE);
        assertEquals(1f, TimekeeperPrismStyle.glow(Long.MAX_VALUE / 2), 1e-6f);
    }
}
