package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.data.GooFraction;
import com.mercuriusxeno.goo.data.IRemainderLedger;
import com.mercuriusxeno.goo.data.RemainderLedger;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.resources.ResourceKey;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A price charged three times and refunded three times leaves the purse's
 * whole mB and remainder exactly as they stood, for a fractional price as
 * for a whole one (decisions knives-pay-per-knife-as-each-forms,
 * goo-is-an-int-and-only-the-remainder-is-a-fraction).
 */
class AbilityChargesTest {

    private static final ResourceKey<GooTypeDefinition> CRYSTAL = GooTypes.CRYSTAL;
    private static final int START = 100;
    private static final int KNIVES = 3;

    /** A purse over a whole count of one type and a real remainder ledger. */
    private static final class CountedPurse implements AbilityCharges.Purse {
        private final RemainderLedger ledger = new RemainderLedger();
        private int held = START;

        @Override
        public IRemainderLedger ledger() {
            return ledger;
        }

        @Override
        public int holdings(ResourceKey<GooTypeDefinition> type) {
            return held;
        }

        @Override
        public void withdraw(ResourceKey<GooTypeDefinition> type, int mb) {
            held -= mb;
        }

        @Override
        public void deposit(ResourceKey<GooTypeDefinition> type, int mb) {
            held += mb;
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {25, 12})
    void chargingThreePricesAndRefundingThreeLeavesThePurseAsItStood(int doubledPrice) {
        GooFraction price = GooFraction.of(doubledPrice, 2);
        CountedPurse purse = new CountedPurse();

        for (int knife = 0; knife < KNIVES; knife++) {
            assertTrue(AbilityCharges.charge(purse, CRYSTAL, price), "the purse covers knife " + knife);
        }
        AbilityCharges.refund(purse, CRYSTAL, price.times(KNIVES));

        assertEquals(START, purse.held, "the whole mB should stand as before the charges");
        assertEquals(GooFraction.ZERO, purse.ledger.remainders().of(CRYSTAL), "the remainder should stand at 0");
    }
}
