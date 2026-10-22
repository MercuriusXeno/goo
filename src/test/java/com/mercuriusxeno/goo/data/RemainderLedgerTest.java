package com.mercuriusxeno.goo.data;

import com.mercuriusxeno.goo.type.GooTypes;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.OptionalInt;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The shared exchange logic credits and debits one goo type at its exact value,
 * moving whole mB and keeping the part under 1 mB as the remainder
 * (decision goo-is-an-int-and-only-the-remainder-is-a-fraction).
 */
class RemainderLedgerTest {

    private static final GooFraction SIXTY_FOUR_THIRDS = GooFraction.of(64, 3);

    @Nested
    class Credit {

        @Test
        void halfThenTwoThirdsDepositsNothingThenOneAndKeepsASixth() {
            RemainderLedger ledger = new RemainderLedger();

            assertEquals(0, ledger.creditExact(GooTypes.LEAF, GooFraction.of(1, 2)));
            assertEquals(1, ledger.creditExact(GooTypes.LEAF, GooFraction.of(2, 3)));
            assertEquals(GooFraction.of(1, 6), ledger.remainders().of(GooTypes.LEAF));
        }

        @Test
        void aWholeAmountDepositsItselfAndLeavesTheRemainder() {
            RemainderLedger ledger = new RemainderLedger();
            ledger.creditExact(GooTypes.LEAF, GooFraction.of(1, 6));

            assertEquals(5, ledger.creditExact(GooTypes.LEAF, GooFraction.of(5)));
            assertEquals(GooFraction.of(1, 6), ledger.remainders().of(GooTypes.LEAF));
        }
    }

    @Nested
    class Debit {

        @Test
        void aRemainderOfTwoThirdsPaysFirstSoTwentyOneWithdrawAndAThirdStays() {
            RemainderLedger ledger = new RemainderLedger(GooRemainders.EMPTY.with(GooTypes.LEAF, GooFraction.of(2, 3)));

            assertEquals(OptionalInt.of(21), ledger.debitExact(GooTypes.LEAF, SIXTY_FOUR_THIRDS, 100));
            assertEquals(GooFraction.of(1, 3), ledger.remainders().of(GooTypes.LEAF));
        }

        @Test
        void anEmptyRemainderWithdrawsTwentyTwoAndBanksTwoThirds() {
            RemainderLedger ledger = new RemainderLedger();

            assertEquals(OptionalInt.of(22), ledger.debitExact(GooTypes.LEAF, SIXTY_FOUR_THIRDS, 100));
            assertEquals(GooFraction.of(2, 3), ledger.remainders().of(GooTypes.LEAF));
        }

        @Test
        void aDebitTheAvailableWholeCannotCoverRefusesAndChangesNothing() {
            RemainderLedger ledger = new RemainderLedger();

            assertEquals(OptionalInt.empty(), ledger.debitExact(GooTypes.LEAF, SIXTY_FOUR_THIRDS, 21));
            assertEquals(GooFraction.ZERO, ledger.remainders().of(GooTypes.LEAF));
        }
    }
}
