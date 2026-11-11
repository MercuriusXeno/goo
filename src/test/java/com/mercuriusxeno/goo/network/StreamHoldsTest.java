package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.program.BlockBreakHost;
import com.mercuriusxeno.goo.ability.program.GradualBreak;
import com.mercuriusxeno.goo.data.GooFraction;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

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

        /** A hold's paid reagents last the hold, and a new hold pays again (decision ability-json-names-its-reagent). */
        @Test
        void aHoldStaysPaidUntilANewHoldStarts() {
            StreamHolds holds = new StreamHolds();
            holds.advance(PLAYER, 100);
            holds.markPaid(PLAYER);
            holds.advance(PLAYER, 101);
            assertTrue(holds.paid(PLAYER));
            holds.advance(PLAYER, 110);
            assertFalse(holds.paid(PLAYER));
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

    /**
     * A hold that ends clears the cracks its gradual break drew, whether the
     * player let go or a new hold replaced it
     * (decision gradual-breaks-share-one-break-texture-piece).
     */
    @Nested
    class AnEndedHoldClearsItsCracks {

        private static final BlockPos BLOCK = new BlockPos(4, 64, -2);
        private static final double QUARTER = 0.25;
        private final BlockBreakHost host = mock(BlockBreakHost.class);

        private StreamHolds crackingAt(int tick) {
            StreamHolds holds = new StreamHolds();
            holds.advance(PLAYER, tick);
            holds.marks(PLAYER).gradualBreak().progress(Map.of(BLOCK, QUARTER), host);
            return holds;
        }

        @Test
        void aHoldStillWithinItsGapKeepsItsCracks() {
            StreamHolds holds = crackingAt(100);
            holds.releaseLapsed(102);
            verify(host, never()).crackBlock(BLOCK, GradualBreak.CLEARED);
        }

        @Test
        void aHoldLetGoPastItsGapClearsItsCracks() {
            StreamHolds holds = crackingAt(100);
            holds.releaseLapsed(103);
            verify(host).crackBlock(BLOCK, GradualBreak.CLEARED);
        }

        @Test
        void aNewHoldClearsTheLastHoldsCracks() {
            StreamHolds holds = crackingAt(100);
            holds.advance(PLAYER, 110);
            verify(host).crackBlock(BLOCK, GradualBreak.CLEARED);
        }
    }

    /** A stream's cost drains in whole shares over its hold (decision stream-delivery-held-cone). */
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

        @Test
        void aFractionalCostDrainsExactlyAcrossCharges() {
            GooFraction twelveAndAHalf = GooFraction.of(25, 2);
            int firstCharge = IntStream.rangeClosed(1, TICKS_PER_CHARGE)
                    .map(held -> StreamHolds.shareAt(twelveAndAHalf, TICKS_PER_CHARGE, held)).sum();
            int twoCharges = IntStream.rangeClosed(1, 2 * TICKS_PER_CHARGE)
                    .map(held -> StreamHolds.shareAt(twelveAndAHalf, TICKS_PER_CHARGE, held)).sum();

            assertEquals(12, firstCharge);
            assertEquals(25, twoCharges);
        }
    }
}
