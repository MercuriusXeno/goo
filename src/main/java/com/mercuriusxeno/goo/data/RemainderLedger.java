package com.mercuriusxeno.goo.data;

import com.mercuriusxeno.goo.type.GooTypeDefinition;
import net.minecraft.resources.ResourceKey;
import java.util.OptionalInt;

/**
 * The remainder exchange every goo store calls rather than reimplements: a credit
 * adds the exact amount to the remainder and answers the whole mB it reaches, and a
 * debit draws the remainder first, answers the whole mB still owed rounded up and
 * banks the overpayment as the new remainder. So 1/2 then 2/3 deposit 0 then 1 and
 * keep 1/6, and 64/3 debited three times from nothing withdraws 22, 21, 21.
 * decision one-remainder-capability-injected-into-every-fluid-capability
 * decision goo-is-an-int-and-only-the-remainder-is-a-fraction
 */
public final class RemainderLedger implements IRemainderLedger {

    private static final String ERR_NEGATIVE = "A remainder exchange takes no negative amount: ";

    private GooRemainders held;

    /**
     * A ledger holding no remainder.
     */
    public RemainderLedger() {
        this(GooRemainders.EMPTY);
    }

    /**
     * A ledger holding remainders already accrued.
     *
     * @param held the remainders
     */
    public RemainderLedger(GooRemainders held) {
        this.held = held;
    }

    @Override
    public int creditExact(ResourceKey<GooTypeDefinition> type, GooFraction exact) {
        requireNonNegative(exact);
        GooFraction total = held.of(type).plus(exact);
        int whole = total.floor();
        held = held.with(type, total);
        return whole;
    }

    @Override
    public OptionalInt debitExact(ResourceKey<GooTypeDefinition> type, GooFraction exact, int availableWhole) {
        requireNonNegative(exact);
        GooFraction owed = exact.minus(held.of(type));
        int whole = Math.max(0, owed.ceiling());
        if (whole > availableWhole) {
            return OptionalInt.empty();
        }
        held = held.with(type, GooFraction.of(whole).minus(owed));
        return OptionalInt.of(whole);
    }

    @Override
    public GooRemainders remainders() {
        return held;
    }

    /**
     * Replaces the remainders, the load path of a store.
     *
     * @param loaded the remainders to hold
     */
    public void load(GooRemainders loaded) {
        held = loaded;
    }

    private static void requireNonNegative(GooFraction exact) {
        if (exact.isNegative()) {
            throw new IllegalArgumentException(ERR_NEGATIVE + exact);
        }
    }
}
