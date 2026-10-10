package com.mercuriusxeno.goo.network;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        void aGapOfMoreThanOneTickStartsANewHold() {
            StreamHolds holds = new StreamHolds();
            holds.advance(PLAYER, 100);
            holds.advance(PLAYER, 101);
            assertEquals(1, holds.advance(PLAYER, 104));
        }

        /** A client's stream ticks jitter against the server's: one can land a tick late. */
        @Test
        void oneLateTickContinuesTheHold() {
            StreamHolds holds = new StreamHolds();
            holds.advance(PLAYER, 100);
            holds.advance(PLAYER, 101);
            assertEquals(3, holds.advance(PLAYER, 103));
        }

        /** Two stream ticks landing in one server tick run the hold once, the second running nothing. */
        @Test
        void aSecondStreamTickInOneServerTickRunsNothing() {
            StreamHolds holds = new StreamHolds();
            holds.advance(PLAYER, 100);
            assertEquals(2, holds.advance(PLAYER, 101));
            assertEquals(0, holds.advance(PLAYER, 101));
            assertEquals(3, holds.advance(PLAYER, 102));
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

    /** A hold keeps its marks while it lasts and a new hold starts fresh (decision decay-gnats-degrade-each-block-once). */
    @Nested
    class MarksLastTheHold {

        private static final BlockPos BLOCK = new BlockPos(4, 64, -2);

        @Test
        void aSteppedPositionReadsSteppedForTheRestOfTheHold() {
            StreamHolds holds = new StreamHolds();
            holds.advance(PLAYER, 100);
            holds.marks(PLAYER).noteStepped(BLOCK);
            holds.advance(PLAYER, 101);
            assertTrue(holds.marks(PLAYER).stepped(BLOCK));
            assertFalse(holds.marks(PLAYER).stepped(BLOCK.above()));
        }

        @Test
        void aNewHoldForgetsTheLastHoldsMarks() {
            StreamHolds holds = new StreamHolds();
            holds.advance(PLAYER, 100);
            holds.marks(PLAYER).noteStepped(BLOCK);
            holds.advance(PLAYER, 103);
            assertFalse(holds.marks(PLAYER).stepped(BLOCK));
        }

        @Test
        void eachPlayersMarksAreItsOwn() {
            StreamHolds holds = new StreamHolds();
            holds.advance(PLAYER, 100);
            holds.advance(OTHER, 100);
            holds.marks(PLAYER).noteStepped(BLOCK);
            assertFalse(holds.marks(OTHER).stepped(BLOCK));
        }
    }

    /** A hold touches each block once, and a new hold forgets what the last one touched (decision signal-wave-toggles-each-device-once). */
    @Nested
    class TouchOnce {

        private static final BlockPos LEVER = new BlockPos(4, 64, 2);

        @Test
        void aHoldTouchesEachBlockOnce() {
            StreamHolds holds = new StreamHolds();
            holds.advance(PLAYER, 100);
            assertTrue(holds.touchOnce(PLAYER, LEVER));
            holds.advance(PLAYER, 101);
            assertFalse(holds.touchOnce(PLAYER, LEVER));
        }

        @Test
        void aNewHoldTouchesTheBlockAgain() {
            StreamHolds holds = new StreamHolds();
            holds.advance(PLAYER, 100);
            holds.touchOnce(PLAYER, LEVER);
            holds.advance(PLAYER, 103);
            assertTrue(holds.touchOnce(PLAYER, LEVER));
        }

        @Test
        void noHoldTouchesNothing() {
            assertFalse(new StreamHolds().touchOnce(PLAYER, LEVER));
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
