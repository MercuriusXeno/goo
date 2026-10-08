package com.mercuriusxeno.goo.network;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A stream hold counts consecutive server ticks per player, and its ticks
 * drain the ability's cost exactly once per ticks_per_charge of hold
 * (decision stream-delivery-held-cone).
 */
class StreamHoldsTest {

    private static final UUID PLAYER = new UUID(1, 2);
    private static final UUID OTHER = new UUID(3, 4);
    private static final int COST = 1000;
    private static final int TICKS_PER_CHARGE = 20;

    @Nested
    class Advance {

        @Test
        void consecutiveTicksContinueTheHold() {
            StreamHolds holds = new StreamHolds();
            holds.advance(PLAYER, 100);
            holds.advance(PLAYER, 101);
            assertEquals(3, holds.advance(PLAYER, 102));
        }

        @Test
        void aGapStartsANewHold() {
            StreamHolds holds = new StreamHolds();
            holds.advance(PLAYER, 100);
            holds.advance(PLAYER, 101);
            assertEquals(1, holds.advance(PLAYER, 103));
        }

        @Test
        void eachPlayerHoldsOnItsOwn() {
            StreamHolds holds = new StreamHolds();
            holds.advance(PLAYER, 100);
            holds.advance(PLAYER, 101);
            assertEquals(1, holds.advance(OTHER, 101));
        }

        @Test
        void clearDropsEveryHold() {
            StreamHolds holds = new StreamHolds();
            holds.advance(PLAYER, 100);
            holds.clear();
            assertEquals(1, holds.advance(PLAYER, 101));
        }
    }

    /** Each block a stream holds counts its own hold (decision unmake-waves-dissolve-by-crucible-cost). */
    @Nested
    class ShareAt {

        private int drainedOver(int cost, int ticksPerCharge, int ticks) {
            return IntStream.rangeClosed(1, ticks).map(held -> StreamHolds.shareAt(cost, ticksPerCharge, held)).sum();
        }

        @Test
        void oneChargeOfHoldDrainsTheCostExactly() {
            assertEquals(COST, drainedOver(COST, TICKS_PER_CHARGE, TICKS_PER_CHARGE));
        }

        @Test
        void anUnevenSplitStillDrainsTheCostPerCharge() {
            assertEquals(2 * 1000, drainedOver(1000, 7, 14));
        }

        @Test
        void eachTickDrainsItsShare() {
            assertEquals(COST / TICKS_PER_CHARGE, StreamHolds.shareAt(COST, TICKS_PER_CHARGE, 1));
        }
    }
}
