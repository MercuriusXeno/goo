package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.data.GooFraction;

/**
 * What a held stream pays as each thing it does happens, for a stream whose
 * delivery drains on placement rather than by the ticks it is held: a share
 * of the ability's cost for each thing it lays, or a step's own price for a
 * unit the cost does not price, such as a hit on a mob. Any other stream has
 * paid by the tick already, so what it does is free.
 * goop-drains-per-cubic-meter-of-slime-placed
 * decay-pays-per-block-degraded-and-bite
 */
public interface PlacementPrice {

    /** The price of a stream paying by the tick: everything it does happens unpaid. */
    PlacementPrice FREE = new PlacementPrice() {
        @Override
        public boolean pay(int placementsPerCost) {
            return true;
        }

        @Override
        public boolean affords(GooFraction unitPrice) {
            return true;
        }

        @Override
        public boolean pay(GooFraction unitPrice) {
            return true;
        }
    };

    /**
     * Charges one placement's share of the ability's cost, where the player covers it.
     *
     * @param placementsPerCost the placements one whole cost buys, so one placement costs the cost over this
     * @return true where the placement was paid for and may land
     */
    boolean pay(int placementsPerCost);

    /**
     * Whether the player covers a step's own price for one unit, charging nothing.
     *
     * @param unitPrice the exact mB of the step's price field
     * @return true where the unit may happen
     */
    boolean affords(GooFraction unitPrice);

    /**
     * Charges a step's own price for one unit that happened, where the player covers it.
     *
     * @param unitPrice the exact mB of the step's price field
     * @return true where the unit was paid for
     */
    boolean pay(GooFraction unitPrice);
}
