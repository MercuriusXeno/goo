package com.mercuriusxeno.goo.data;

import com.mercuriusxeno.goo.type.GooTypeDefinition;
import net.minecraft.resources.ResourceKey;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.ToIntFunction;

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
     * Credits each positive type's exact amount.
     *
     * @param exacts each type's exact amount
     * @return the whole mB to deposit of each type, a type depositing nothing left out
     */
    default Map<ResourceKey<GooTypeDefinition>, Integer> creditAllExact(
            Map<ResourceKey<GooTypeDefinition>, GooFraction> exacts) {
        Map<ResourceKey<GooTypeDefinition>, Integer> deposited = new HashMap<>();
        exacts.forEach((type, exact) -> {
            if (exact.isPositive()) {
                int whole = creditExact(type, exact);
                if (whole > 0) {
                    deposited.put(type, whole);
                }
            }
        });
        return deposited;
    }

    /**
     * Debits several types at once, all or none: each positive type's exact amount
     * debits the ledger only where the available whole of every type covers its own
     * debit, so a refusal changes no remainder.
     *
     * @param exacts    each type's exact amount
     * @param available the whole mB the store holds of a type
     * @return the whole mB to withdraw of each type, or empty where any type cannot be covered
     */
    default Optional<Map<ResourceKey<GooTypeDefinition>, Integer>> debitAllExact(
            Map<ResourceKey<GooTypeDefinition>, GooFraction> exacts,
            ToIntFunction<ResourceKey<GooTypeDefinition>> available) {
        RemainderLedger preview = new RemainderLedger(remainders());
        for (Map.Entry<ResourceKey<GooTypeDefinition>, GooFraction> entry : exacts.entrySet()) {
            if (entry.getValue().isPositive()
                    && preview.debitExact(entry.getKey(), entry.getValue(), available.applyAsInt(entry.getKey())).isEmpty()) {
                return Optional.empty();
            }
        }
        Map<ResourceKey<GooTypeDefinition>, Integer> withdrawn = new HashMap<>();
        exacts.forEach((type, exact) -> {
            if (exact.isPositive()) {
                withdrawn.put(type, debitExact(type, exact, available.applyAsInt(type)).orElseThrow());
            }
        });
        return Optional.of(withdrawn);
    }

    /**
     * The remainder of every goo type the ledger holds.
     *
     * @return the remainders
     */
    GooRemainders remainders();
}
