package com.mercuriusxeno.goo.client.network;

import com.mercuriusxeno.goo.ability.program.OutsiderStep;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler.ClientAbility;
import com.mercuriusxeno.goo.item.GooFormat;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * How many times, or how long, the goo a player holds affords an ability.
 * panel-and-petals-count-uses-or-seconds
 * reagent-count-is-the-smaller-bound
 *
 * @param count the whole uses, or the ticks a timed ability is sustained
 * @param timed true for a stream, channel or durative ability, which the holdings sustain for a time
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
        return reckon(ability, holdings, reagentSets, StandingUpkeep.NONE);
    }

    /**
     * Reckons what the holdings afford of an ability beside the upkeep already standing on its
     * type: a time count draws its own rate and the standing upkeep together, while a one-shot's
     * shots can all fire this instant, so its use count ignores what stands.
     * time-counts-net-standing-upkeep
     *
     * @param ability     the synced ability
     * @param holdings    the mB the player holds of the ability's type across the inventory
     * @param reagentSets the whole sets of the ability's consumed items the inventory holds
     * @param standing    the upkeep already standing on the ability's type
     * @return the count, empty for a dynamically priced ability
     */
    public static Optional<AffordedCount> reckon(ClientAbility ability, int holdings, int reagentSets,
                                                 StandingUpkeep standing) {
        return reckon(ability, holdings, reagentSets, standing, OptionalDouble.empty());
    }

    /**
     * Reckons what the holdings afford of an ability beside the upkeep already standing on its
     * type and the Outsider room the player stands in: in the room, Outsider counts the seconds
     * the holdings sustain its growth, the price rising as the room widens.
     * time-counts-net-standing-upkeep
     * decision outsider-panel-reads-the-seconds-the-void-affords
     *
     * @param ability     the synced ability
     * @param holdings    the mB the player holds of the ability's type across the inventory
     * @param reagentSets the whole sets of the ability's consumed items the inventory holds
     * @param standing    the upkeep already standing on the ability's type
     * @param roomExtent  the extent of the Outsider room the player stands in, empty outside the pocket
     * @return the count, empty for a dynamically priced ability
     */
    public static Optional<AffordedCount> reckon(ClientAbility ability, int holdings, int reagentSets,
                                                 StandingUpkeep standing, OptionalDouble roomExtent) {
        Optional<OutsiderStep> outsider = ability.outsider();
        if (outsider.isPresent() && roomExtent.isPresent()) {
            return Optional.of(new AffordedCount(outsider.get().growthTicksAfforded(roomExtent.getAsDouble(),
                    holdings, standing.perTick()), true));
        }
        return fixedPriceCount(ability, holdings, reagentSets, standing);
    }

    /**
     * The uses or time a fixed price affords, beside the upkeep standing on the type.
     *
     * @param ability     the synced ability
     * @param holdings    the mB the player holds of the ability's type across the inventory
     * @param reagentSets the whole sets of the ability's consumed items the inventory holds
     * @param standing    the upkeep already standing on the ability's type
     * @return the count, empty for a dynamically priced ability
     */
    private static Optional<AffordedCount> fixedPriceCount(ClientAbility ability, int holdings, int reagentSets,
                                                           StandingUpkeep standing) {
        if (!ability.countsUses()) {
            return Optional.empty();
        }
        int reagentBound = ability.consumes().isEmpty() ? Integer.MAX_VALUE : reagentSets;
        if (ability.paysWhileHeld()) {
            // a hold pays its reagents once, so holding none sustains no tick
            return Optional.of(new AffordedCount(reagentBound > 0 ? ticksSustained(ability, holdings, standing) : 0,
                    true));
        }
        return Optional.of(new AffordedCount(Math.min(holdings / ability.cost(), reagentBound), false));
    }

    /**
     * The ticks the holdings pay for the ability's own rate and the standing upkeep together:
     * a durative ability's upkeep, halved under a standing Extender unless it is the Extender, or a stream's
     * or channel's cost each ticksPerCharge, scaled through by ticksPerCharge so 1000 over
     * 1000 a 60 ticks reads 60 rather than a hair under.
     *
     * @param ability  the held ability
     * @param holdings the mB the player holds of its type
     * @param standing the upkeep already standing on its type
     * @return the whole ticks
     */
    private static long ticksSustained(ClientAbility ability, int holdings, StandingUpkeep standing) {
        if (ability.upkeep() > 0) {
            // time-counts-net-standing-upkeep: a durative ability standing on the glove is in the standing upkeep already
            double own = standing.paying().contains(ability.id()) ? 0
                    : standing.ownDurativeUpkeep(ability.upkeep(), ability.extendsOthers());
            return (long) (holdings / (own + standing.perTick()));
        }
        int ticksPerCharge = ability.delivery().ticksPerCharge();
        return (long) ((double) holdings * ticksPerCharge / (ability.cost() + standing.perTick() * ticksPerCharge));
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
