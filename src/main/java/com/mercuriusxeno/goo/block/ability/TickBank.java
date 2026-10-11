package com.mercuriusxeno.goo.block.ability;

/**
 * The ticks a timekeeper prism stores: charge it banked by standing in the
 * world and charge aeon goo landing on it fed it, with no cap. Tick spends
 * the charge to move day time forward, the fed charge first; Rewind
 * withdraws only the standing charge, as aeon goo.
 * timekeeper-prism-banks-ticks-forward-only
 *
 * @param fed          charge aeon landings fed it
 * @param standing     charge it banked by standing
 * @param spendPerTick the most charge one held tick of Tick spends; zero while the prism banks nothing
 */
public record TickBank(long fed, long standing, int spendPerTick) {

    /** A prism that banks no ticks. */
    public static final TickBank NONE = new TickBank(0L, 0L, 0);

    /**
     * Answers whether the prism banks ticks, which its combo's bank_ticks step starts.
     *
     * @return true once a combo banks ticks on the prism
     */
    public boolean banking() {
        return spendPerTick > 0;
    }

    /**
     * @return the charge banked, fed and standing together
     */
    public long total() {
        return fed + standing;
    }

    /**
     * Banks one tick of standing.
     *
     * @param perTick  the charge standing banks a tick
     * @param spending the most charge one held tick of Tick spends
     * @return the bank after the tick
     */
    public TickBank stood(int perTick, int spending) {
        return new TickBank(fed, standing + perTick, spending);
    }

    /**
     * Banks the charge an aeon landing feeds it.
     *
     * @param charge the charge fed
     * @return the bank after the landing
     */
    public TickBank fedWith(long charge) {
        return new TickBank(fed + Math.max(0L, charge), standing, spendPerTick);
    }

    /**
     * @return the charge one held tick of Tick spends now: its rate, or what remains
     */
    public long spendable() {
        return Math.min(total(), spendPerTick);
    }

    /**
     * Spends charge, the fed charge first.
     *
     * @param charge the charge spent, at most the total
     * @return the bank after spending
     */
    public TickBank afterSpending(long charge) {
        long fromFed = Math.min(fed, charge);
        return new TickBank(fed - fromFed, standing - (charge - fromFed), spendPerTick);
    }

    /**
     * Withdraws standing charge.
     *
     * @param charge the charge withdrawn, at most the standing charge
     * @return the bank after withdrawing
     */
    public TickBank afterWithdrawing(long charge) {
        return new TickBank(fed, standing - charge, spendPerTick);
    }
}
