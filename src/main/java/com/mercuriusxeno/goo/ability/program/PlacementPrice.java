package com.mercuriusxeno.goo.ability.program;

/**
 * What a held stream pays as each thing it lays lands, for a stream whose
 * delivery drains on placement rather than by the ticks it is held; any
 * other stream has paid by the tick already, so its placements are free.
 * goop-drains-per-cubic-meter-of-slime-placed
 */
@FunctionalInterface
public interface PlacementPrice {

    /** The price of a stream paying by the tick: every placement lands unpaid. */
    PlacementPrice FREE = placementsPerCost -> true;

    /**
     * Charges one placement's share of the ability's cost, where the player covers it.
     *
     * @param placementsPerCost the placements one whole cost buys, so one placement costs the cost over this
     * @return true where the placement was paid for and may land
     */
    boolean pay(int placementsPerCost);
}
