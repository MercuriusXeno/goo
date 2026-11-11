package com.mercuriusxeno.goo.ability.outsider;

/**
 * Reckons how long a void holding sustains an Outsider room's growth: ring by
 * ring through the price the hold's feed charges, each tick paying the void
 * upkeep already standing beside it, so the time accounts for the price
 * rising as the room widens. The ring the holding falls short in is walked
 * tick by tick to the last tick it pays.
 * decision outsider-panel-reads-the-seconds-the-void-affords
 */
public final class SustainedGrowth {

    private static final double HALF_BLOCK = 0.5;

    private SustainedGrowth() {
    }

    /**
     * What growing from an extent to the next whole-block mark costs.
     *
     * @param ticks the ticks the hold takes to reach the mark
     * @param fed   the void the feed pays on the way (mB)
     */
    private record RingRun(long ticks, long fed) {
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
        double grown = extent;
        double left = holding;
        long ticks = 0;
        while (true) {
            RingRun run = runToMark(grown, base, perDistance, ringTicks);
            double cost = run.fed() + run.ticks() * standingPerTick;
            if (cost > left) {
                break;
            }
            left -= cost;
            ticks += run.ticks();
            grown = RoomExtent.ringGrowing(grown) + HALF_BLOCK;
        }
        while (true) {
            long feed = RoomExtent.feedFor(grown, base, perDistance, ringTicks);
            if (feed + standingPerTick > left) {
                return ticks;
            }
            left -= feed + standingPerTick;
            grown = RoomExtent.grown(grown, feed, base, perDistance);
            ticks++;
        }
    }

    /**
     * The ticks and void the hold's feed takes from an extent to the next
     * whole-block mark, the feed walked as the hold pays it.
     *
     * @param extent      the room's extent
     * @param base        the void a cell costs at the center (mB)
     * @param perDistance the void each block of Manhattan distance adds (mB)
     * @param ringTicks   the ticks every ring takes
     * @return the run to the mark
     */
    private static RingRun runToMark(double extent, int base, int perDistance, int ringTicks) {
        int ring = RoomExtent.ringGrowing(extent);
        double grown = extent;
        long ticks = 0;
        long fed = 0;
        while (RoomExtent.ringGrowing(grown) == ring) {
            long feed = RoomExtent.feedFor(grown, base, perDistance, ringTicks);
            grown = RoomExtent.grown(grown, feed, base, perDistance);
            fed += feed;
            ticks++;
        }
        return new RingRun(ticks, fed);
    }
}
