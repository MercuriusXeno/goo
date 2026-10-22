package com.mercuriusxeno.goo.data;

import com.mercuriusxeno.goo.type.GooTypeDefinition;
import net.minecraft.resources.ResourceKey;
import java.util.OptionalInt;

/**
 * A goo store's remainder ledger: what a producer credits and a consumer debits
 * at the exact value, so a caller takes the ledger and never a fluid handler.
 * The store moves the whole mB each call answers; the part under 1 mB stays in the ledger.
 * decision one-remainder-capability-injected-into-every-fluid-capability
 * decision goo-is-an-int-and-only-the-remainder-is-a-fraction
 */
public interface IRemainderLedger {

    /**
     * Credits an exact amount of one goo type.
     *
     * @param type  the goo type
     * @param exact the exact amount, never negative
     * @return the whole mB the store deposits
     */
    int creditExact(ResourceKey<GooTypeDefinition> type, GooFraction exact);

    /**
     * Debits an exact amount of one goo type, the remainder paying first.
     *
     * @param type           the goo type
     * @param exact          the exact amount, never negative
     * @param availableWhole the whole mB the store holds of that type
     * @return the whole mB the store withdraws, or empty where the available whole cannot cover it
     */
    OptionalInt debitExact(ResourceKey<GooTypeDefinition> type, GooFraction exact, int availableWhole);

    /**
     * The remainder of every goo type the ledger holds.
     *
     * @return the remainders
     */
    GooRemainders remainders();
}
