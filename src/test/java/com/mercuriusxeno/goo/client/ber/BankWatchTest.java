package com.mercuriusxeno.goo.client.ber;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A timekeeper prism's bank reads as spending for a moment after its charge
 * falls, and not while it stands or grows
 * (decision timekeeper-prism-banks-ticks-forward-only).
 */
class BankWatchTest {

    private static final BlockPos PRISM = new BlockPos(1, 2, 3);
    private static final long START = 100L;

    @Test
    void aGrowingBankIsNotSpending() {
        BankWatch watch = new BankWatch();

        assertFalse(watch.spending(PRISM, 10, START));
        assertFalse(watch.spending(PRISM, 11, START + 1));
    }

    @Test
    void aFallingBankIsSpendingForAMoment() {
        BankWatch watch = new BankWatch();
        watch.spending(PRISM, 500, START);

        assertTrue(watch.spending(PRISM, 460, START + 1));
        assertTrue(watch.spending(PRISM, 460, START + 1 + BankWatch.SPENDING_HOLD_TICKS));
        assertFalse(watch.spending(PRISM, 460, START + 2 + BankWatch.SPENDING_HOLD_TICKS));
    }
}
