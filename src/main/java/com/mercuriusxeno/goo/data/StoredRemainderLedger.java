package com.mercuriusxeno.goo.data;

import com.mercuriusxeno.goo.type.GooTypeDefinition;
import net.minecraft.resources.ResourceKey;
import java.util.OptionalInt;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A remainder ledger over remainders a store keeps elsewhere, on a player's
 * attachment, a canister's content or a server's saved data: each exchange reads
 * the stored remainders, runs the shared exchange logic, and writes them back.
 * decision one-remainder-capability-injected-into-every-fluid-capability
 *
 * @param stored reads the remainders the store holds
 * @param store  writes the remainders back to the store
 */
public record StoredRemainderLedger(Supplier<GooRemainders> stored, Consumer<GooRemainders> store)
        implements IRemainderLedger {

    @Override
    public int creditExact(ResourceKey<GooTypeDefinition> type, GooFraction exact) {
        RemainderLedger exchange = new RemainderLedger(stored.get());
        int whole = exchange.creditExact(type, exact);
        storeChanged(exchange.remainders());
        return whole;
    }

    @Override
    public OptionalInt debitExact(ResourceKey<GooTypeDefinition> type, GooFraction exact, int availableWhole) {
        RemainderLedger exchange = new RemainderLedger(stored.get());
        OptionalInt whole = exchange.debitExact(type, exact, availableWhole);
        storeChanged(exchange.remainders());
        return whole;
    }

    @Override
    public GooRemainders remainders() {
        return stored.get();
    }

    private void storeChanged(GooRemainders remainders) {
        if (!remainders.equals(stored.get())) {
            store.accept(remainders);
        }
    }
}
