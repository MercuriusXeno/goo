package com.mercuriusxeno.goo.block.ability;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A timekeeper's bank grows by standing and by feeding with no cap, Tick
 * spends the fed charge before the standing, and only the standing charge
 * withdraws (decision timekeeper-prism-banks-ticks-forward-only).
 */
class TickBankTest {

    private static final int SPEND = 40;

    @Test
    void aPrismBanksOnlyOnceItsComboStands() {
        assertFalse(TickBank.NONE.banking());
        assertTrue(TickBank.NONE.stood(1, SPEND).banking());
    }

    @Test
    void standingAndFeedingBankWithNoCap() {
        TickBank bank = TickBank.NONE.stood(1, SPEND).fedWith(Long.MAX_VALUE / 2).stood(1, SPEND);

        assertEquals(2L, bank.standing());
        assertEquals(Long.MAX_VALUE / 2 + 2, bank.total());
    }

    @Test
    void tickSpendsItsRateOrWhatRemains() {
        assertEquals(SPEND, new TickBank(100, 100, SPEND).spendable());
        assertEquals(7L, new TickBank(3, 4, SPEND).spendable());
    }

    @Test
    void spendingTakesTheFedChargeFirst() {
        TickBank after = new TickBank(30, 100, SPEND).afterSpending(SPEND);

        assertEquals(0L, after.fed());
        assertEquals(90L, after.standing());
    }

    @Test
    void withdrawingTakesOnlyStandingCharge() {
        TickBank after = new TickBank(30, 100, SPEND).afterWithdrawing(60);

        assertEquals(30L, after.fed());
        assertEquals(40L, after.standing());
    }
}
