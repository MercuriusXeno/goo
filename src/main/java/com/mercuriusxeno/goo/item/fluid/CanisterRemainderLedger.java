package com.mercuriusxeno.goo.item.fluid;

import com.mercuriusxeno.goo.data.GooRemainders;
import com.mercuriusxeno.goo.data.IRemainderLedger;
import com.mercuriusxeno.goo.data.StoredRemainderLedger;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * The remainder ledger a canister item serves through GooCapabilities.REMAINDER_ITEM,
 * held in its fluid content beside the goo, so the remainder travels with the canister.
 * decision one-remainder-capability-injected-into-every-fluid-capability
 */
public final class CanisterRemainderLedger {

    private CanisterRemainderLedger() {
    }

    /**
     * The ledger over the canister an item access holds.
     *
     * @param access the canister's item access
     * @return the ledger
     */
    public static IRemainderLedger of(ItemAccess access) {
        return new StoredRemainderLedger(
                () -> CanisterFluidHandler.readContent(access.getResource()).remainders(),
                remainders -> store(access, remainders));
    }

    private static void store(ItemAccess access, GooRemainders remainders) {
        ItemResource item = access.getResource();
        ItemResource changed = CanisterFluidHandler.writeContent(item,
                CanisterFluidHandler.readContent(item).withRemainders(remainders));
        try (var transaction = Transaction.openRoot()) {
            if (access.exchange(changed, access.getAmount(), transaction) == access.getAmount()) {
                transaction.commit();
            }
        }
    }
}
