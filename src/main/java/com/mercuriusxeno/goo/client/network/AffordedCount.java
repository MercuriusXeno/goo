package com.mercuriusxeno.goo.client.network;

import com.mercuriusxeno.goo.client.network.AbilitySyncHandler.ClientAbility;
import com.mercuriusxeno.goo.item.GooFormat;
import java.util.Optional;

/**
 * How many times, or how long, the goo a player holds affords an ability.
 * panel-and-petals-count-uses-or-seconds
 * reagent-count-is-the-smaller-bound
 *
 * @param count the whole uses, or the ticks a timed ability is sustained
 * @param timed true for a stream, channel or brew, which the holdings sustain for a time
 */
public record AffordedCount(long count, boolean timed) {

    private static final String USES_PREFIX = "x";
    private static final int TICKS_PER_SECOND = 20;

    /**
     * Reckons what the holdings afford of an ability.
     *
     * @param ability     the synced ability
     * @param holdings    the mB the player holds of the ability's type across the inventory
     * @param reagentSets the whole sets of the ability's consumed items the inventory holds
     * @return the count, empty for a dynamically priced ability
     */
    public static Optional<AffordedCount> reckon(ClientAbility ability, int holdings, int reagentSets) {
        if (!ability.countsUses()) {
            return Optional.empty();
        }
        int reagentBound = ability.consumes().isEmpty() ? Integer.MAX_VALUE : reagentSets;
        if (ability.paysWhileHeld()) {
            // a hold pays its reagents once, so holding none sustains no tick
            return Optional.of(new AffordedCount(reagentBound > 0 ? ticksSustained(ability, holdings) : 0, true));
        }
        return Optional.of(new AffordedCount(Math.min(holdings / ability.cost(), reagentBound), false));
    }

    /**
     * The ticks the holdings sustain: a brew's upkeep each tick, or a stream's or channel's
     * cost each ticksPerCharge.
     *
     * @param ability  the held ability
     * @param holdings the mB the player holds of its type
     * @return the whole ticks
     */
    private static long ticksSustained(ClientAbility ability, int holdings) {
        if (ability.upkeep() > 0) {
            return holdings / ability.upkeep();
        }
        return (long) holdings * ability.delivery().ticksPerCharge() / ability.cost();
    }

    /**
     * The count as the petal and the panel read it, to 3 significant digits truncated.
     * counts-read-three-significant-digits
     *
     * @return "x42.6K" for uses, "24.8d" or "12s" for a time
     */
    public String label() {
        return timed ? GooFormat.formatDuration(count) : USES_PREFIX + GooFormat.formatAmount(count);
    }

    /**
     * Whether the holdings afford no use at all, which dims the petal: no use, or short of a
     * second, which the label reads as "0s".
     *
     * @return true at a count of 0 uses or under a second
     */
    public boolean affordsNone() {
        return count < (timed ? TICKS_PER_SECOND : 1);
    }
}
