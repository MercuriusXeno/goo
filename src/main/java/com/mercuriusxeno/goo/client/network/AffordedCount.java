package com.mercuriusxeno.goo.client.network;

import com.mercuriusxeno.goo.client.network.AbilitySyncHandler.ClientAbility;
import java.util.Optional;

/**
 * How many times, or whole seconds, the goo a player holds affords an ability.
 * panel-and-petals-count-uses-or-seconds
 * reagent-count-is-the-smaller-bound
 *
 * @param count     the whole uses or whole seconds afforded
 * @param inSeconds true for a stream, channel or brew, which the holdings sustain for seconds
 */
public record AffordedCount(int count, boolean inSeconds) {

    private static final int TICKS_PER_SECOND = 20;
    private static final String USES_PREFIX = "x";
    private static final String SECONDS_SUFFIX = "s";

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
            // a hold pays its reagents once, so holding none sustains no second
            return Optional.of(new AffordedCount(reagentBound > 0 ? secondsSustained(ability, holdings) : 0, true));
        }
        return Optional.of(new AffordedCount(Math.min(holdings / ability.cost(), reagentBound), false));
    }

    /**
     * The whole seconds the holdings sustain: a brew's upkeep each tick, or a stream's or
     * channel's cost each ticksPerCharge.
     *
     * @param ability  the held ability
     * @param holdings the mB the player holds of its type
     * @return the whole seconds
     */
    private static int secondsSustained(ClientAbility ability, int holdings) {
        if (ability.upkeep() > 0) {
            return holdings / (ability.upkeep() * TICKS_PER_SECOND);
        }
        long paidTicks = (long) holdings * ability.delivery().ticksPerCharge();
        return (int) (paidTicks / ((long) ability.cost() * TICKS_PER_SECOND));
    }

    /**
     * The count as the petal and the panel read it.
     *
     * @return "x3" for uses, "12s" for seconds
     */
    public String label() {
        return inSeconds ? count + SECONDS_SUFFIX : USES_PREFIX + count;
    }

    /**
     * Whether the holdings afford no use at all, which dims the petal.
     *
     * @return true at a count of 0
     */
    public boolean affordsNone() {
        return count <= 0;
    }
}
