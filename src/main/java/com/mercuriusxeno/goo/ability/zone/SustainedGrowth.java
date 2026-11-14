package com.mercuriusxeno.goo.ability.zone;

/**
 * Reckons how long a void holding sustains a Zone room's growth, in
 * closed form. The hold feeds ring k its price, 8k base plus 12k squared
 * per distance, over the ring time, beside the void upkeep standing each
 * tick, so the void spent growing through whole rings is a cubic in the last
 * ring, and its derivative is the price of that ring and the upkeep over its
 * ring time. Newton's method on that derivative finds the last whole ring the
 * holding pays; the ring it falls short in is solved from the feed's own
 * schedule, so the count equals the ticks a hold pays.
 * decision outsider-panel-reads-the-seconds-the-void-affords
 */
public final class SustainedGrowth {

    /** Newton's steps at most: from an overshoot a cubic's root is reached within a few dozen. */
    private static final int NEWTON_STEPS = 64;
    /** A Newton step this short has settled on the root. */
    private static final double SETTLED = 1.0E-6;

    private SustainedGrowth() {
    }

    /**
     * One ring as the hold feeds it: a ring priced at or past the ring time
     * is fed the ceiling of its price's share of each tick, ceil(P j / T)
     * after j ticks, and takes the ring time; one priced under it is fed
     * 1 mB a tick and takes as many ticks as its price.
     *
     * @param price     the ring's price (mB)
     * @param ringTicks the ring time
     * @param standing  the void upkeep standing each tick (mB)
     */
    private record Ring(long price, int ringTicks, double standing) {

        private boolean fedByShare() {
            return price >= ringTicks;
        }

        private long ticksWhole() {
            return fedByShare() ? ringTicks : price;
        }

        private long ticksHeld(long fed) {
            return fedByShare() ? fed * ringTicks / price : fed;
        }

        private long fedAfter(long ticks) {
            return fedByShare() ? Math.ceilDiv(price * ticks, ringTicks) : ticks;
        }

        private double feedPerTick() {
            return fedByShare() ? price / (double) ringTicks : 1;
        }

        /**
         * @param fed the void already fed into the ring (mB)
         * @return the void the ring's last ticks cost, upkeep included (mB)
         */
        private double costToFinish(long fed) {
            return price - fed + (ticksWhole() - ticksHeld(fed)) * standing;
        }

        /**
         * @param fed   the void already fed into the ring (mB)
         * @param ticks the ticks more
         * @return the void those ticks cost, upkeep included (mB)
         */
        private double costOf(long fed, long ticks) {
            return fedAfter(ticksHeld(fed) + ticks) - fed + ticks * standing;
        }

        /**
         * The ticks a holding pays into the ring before a tick asks more than
         * is left: the feed's straight line solved, then stepped back off
         * the ceiling's rounding, which a feed of at least 1 mB a tick
         * clears in one step.
         *
         * @param fed  the void already fed into the ring (mB)
         * @param left the void left to pay with (mB)
         * @return the ticks paid, the ring's remaining ticks at most
         */
        private long ticksPaid(long fed, double left) {
            long held = ticksHeld(fed);
            double line = (left + fed - held * feedPerTick()) / (feedPerTick() + standing);
            long ticks = Math.clamp((long) Math.floor(line), 0, ticksWhole() - held);
            while (ticks > 0 && costOf(fed, ticks) > left) {
                ticks--;
            }
            return ticks;
        }
    }

    /**
     * The ticks a holding pays the hold's feed and the standing upkeep
     * together, before a tick asks more than is left.
     *
     * @param extent          the room's extent
     * @param holding         the void held (mB)
     * @param standingPerTick the void upkeep already standing each tick (mB)
     * @param base            the void a cell costs at the center (mB)
     * @param perDistance     the void each block of Manhattan distance adds (mB)
     * @param ringTicks       the ticks every ring takes
     * @return the ticks paid
     */
    public static long ticksAfforded(double extent, double holding, double standingPerTick, int base,
                                     int perDistance, int ringTicks) {
        int first = RoomExtent.ringGrowing(extent);
        Ring growing = ring(first, base, perDistance, ringTicks, standingPerTick);
        long fed = RoomExtent.fedIntoRing(extent, growing.price());
        double finish = growing.costToFinish(fed);
        if (finish > holding) {
            return growing.ticksPaid(fed, holding);
        }
        long ticks = growing.ticksWhole() - growing.ticksHeld(fed);
        if (base <= 0 && perDistance <= 0) {
            // Every ring is priced 1 mB and takes 1 tick.
            return ticks + (long) Math.floor((holding - finish) / (1 + standingPerTick));
        }
        return ticks + ticksFromRing(first + 1, holding - finish, base, perDistance, ringTicks, standingPerTick);
    }

    /**
     * The ticks a holding pays from the start of a ring on: the few rings
     * priced under the ring time one by one, since a ring's price grows with
     * the ring, then the whole rings past them in closed form, then the ring
     * the holding falls short in.
     *
     * @param from        the ring growing starts in, on its whole-block mark
     * @param holding     the void left to pay with (mB)
     * @param base        the void a cell costs at the center (mB)
     * @param perDistance the void each block of Manhattan distance adds (mB)
     * @param ringTicks   the ring time
     * @param standing    the void upkeep standing each tick (mB)
     * @return the ticks paid
     */
    private static long ticksFromRing(int from, double holding, int base, int perDistance, int ringTicks,
                                      double standing) {
        double left = holding;
        long ticks = 0;
        int next = from;
        while (RoomExtent.ringPrice(next, base, perDistance) < ringTicks) {
            Ring under = ring(next, base, perDistance, ringTicks, standing);
            if (under.costToFinish(0) > left) {
                return ticks + under.ticksPaid(0, left);
            }
            left -= under.costToFinish(0);
            ticks += under.ticksWhole();
            next++;
        }
        WholeRings whole = new WholeRings(next, base, perDistance, ringTicks, standing);
        long last = whole.lastPaid(left);
        left -= whole.cost(last);
        ticks += (last - next + 1) * ringTicks;
        return ticks + ring((int) last + 1, base, perDistance, ringTicks, standing).ticksPaid(0, left);
    }

    private static Ring ring(int ring, int base, int perDistance, int ringTicks, double standing) {
        return new Ring(RoomExtent.ringPrice(ring, base, perDistance), ringTicks, standing);
    }

    /**
     * Whole rings from one on, each priced at or past the ring time and so
     * taking the ring time: the void through ring K is
     * S(K) = the rings' prices summed plus the upkeep over their ring times,
     * and S'(K) = 8 base (K + 1/2) + 12 perDistance (K squared + K) + 2 perDistance + T upkeep.
     *
     * @param first       the first whole ring
     * @param base        the void a cell costs at the center (mB)
     * @param perDistance the void each block of Manhattan distance adds (mB)
     * @param ringTicks   the ring time
     * @param standing    the void upkeep standing each tick (mB)
     */
    private record WholeRings(int first, int base, int perDistance, int ringTicks, double standing) {

        private static final double HALF = 0.5;
        private static final int CELLS_PER_RING = 8;
        private static final int DISTANCE_PER_RING_SQUARED = 12;
        private static final int DISTANCE_PER_RING_CONSTANT = 2;

        private double cost(double last) {
            return PlatformSpiral.ringsCost(first, last, base, perDistance)
                    + Math.max(0, last - first + 1) * ringTicks * standing;
        }

        private double slope(double last) {
            return CELLS_PER_RING * base * (last + HALF)
                    + DISTANCE_PER_RING_SQUARED * perDistance * (last * last + last)
                    + DISTANCE_PER_RING_CONSTANT * perDistance + ringTicks * standing;
        }

        /**
         * The last whole ring a holding pays: S(K) = left solved by Newton's
         * method, which on a rising convex cubic overshoots once and then
         * closes from above, its root floored and checked against the sums.
         *
         * @param left the void left to pay with (mB)
         * @return the last whole ring paid, one short of the first where none is
         */
        private long lastPaid(double left) {
            if (cost(first) > left) {
                return first - 1L;
            }
            double root = first;
            for (int step = 0; step < NEWTON_STEPS; step++) {
                double move = (cost(root) - left) / slope(root);
                root -= move;
                if (Math.abs(move) < SETTLED) {
                    break;
                }
            }
            long last = Math.max(first, (long) Math.floor(root));
            if (cost(last) > left) {
                last--;
            } else if (cost(last + 1) <= left) {
                last++;
            }
            return last;
        }
    }
}
