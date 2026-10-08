package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.data.GooValue;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
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
    class AdvanceBlock {

        private static final BlockPos NEAR = new BlockPos(1, 2, 3);
        private static final BlockPos FAR = new BlockPos(4, 5, 6);

        @Test
        void aBlockHeldEachTickCountsOn() {
            StreamHolds holds = new StreamHolds();
            holds.advanceBlock(PLAYER, NEAR, 100);
            holds.advanceBlock(PLAYER, NEAR, 101);
            assertEquals(3, holds.advanceBlock(PLAYER, NEAR, 102));
        }

        @Test
        void aBlockTheStreamLeftStartsOver() {
            StreamHolds holds = new StreamHolds();
            holds.advanceBlock(PLAYER, NEAR, 100);
            holds.advanceBlock(PLAYER, NEAR, 101);
            holds.advanceBlock(PLAYER, FAR, 102);
            assertEquals(1, holds.advanceBlock(PLAYER, NEAR, 102 + StreamHolds.HOLD_GRACE_TICKS));
        }

        /** Two stream ticks landing in one server tick leave the next empty; the hold carries over it. */
        @Test
        void aServerTickTheStreamMissedKeepsTheHold() {
            StreamHolds holds = new StreamHolds();
            holds.advanceBlock(PLAYER, NEAR, 100);
            assertEquals(2, holds.advanceBlock(PLAYER, NEAR, 102));
        }

        @Test
        void eachBlockAndPlayerHoldsOnItsOwn() {
            StreamHolds holds = new StreamHolds();
            holds.advanceBlock(PLAYER, NEAR, 100);
            holds.advanceBlock(PLAYER, NEAR, 101);
            assertEquals(1, holds.advanceBlock(PLAYER, FAR, 101));
            assertEquals(1, holds.advanceBlock(OTHER, NEAR, 101));
        }

        @Test
        void clearDropsEveryBlockHold() {
            StreamHolds holds = new StreamHolds();
            holds.advanceBlock(PLAYER, NEAR, 100);
            holds.clear();
            assertEquals(1, holds.advanceBlock(PLAYER, NEAR, 101));
        }
    }

    /** A held mob counts its own hold and keeps the loot it rolled (decision unmake-waves-dissolve-by-crucible-cost). */
    @Nested
    class AdvanceMob {

        private static final UUID CHICKEN = new UUID(5, 6);
        private final GooValue loot = new GooValue(Map.of(GooTypes.VITAL, 100));

        @Test
        void aMobHeldEachTickCountsOn() {
            StreamHolds holds = new StreamHolds();
            holds.advanceMob(PLAYER, CHICKEN, 100);
            assertEquals(2, holds.advanceMob(PLAYER, CHICKEN, 101));
        }

        @Test
        void theLootRollsOnceForTheHold() {
            StreamHolds holds = new StreamHolds();
            AtomicInteger rolls = new AtomicInteger();
            holds.lootOf(PLAYER, CHICKEN, () -> roll(rolls));
            holds.advanceMob(PLAYER, CHICKEN, 100);

            assertEquals(loot, holds.lootOf(PLAYER, CHICKEN, () -> roll(rolls)));
            assertEquals(1, rolls.get());
        }

        @Test
        void aMobTheStreamLeftRollsItsLootAnew() {
            StreamHolds holds = new StreamHolds();
            AtomicInteger rolls = new AtomicInteger();
            holds.lootOf(PLAYER, CHICKEN, () -> roll(rolls));
            holds.advanceMob(PLAYER, CHICKEN, 100);
            holds.advanceMob(PLAYER, new UUID(7, 8), 101 + StreamHolds.HOLD_GRACE_TICKS);

            holds.lootOf(PLAYER, CHICKEN, () -> roll(rolls));

            assertEquals(2, rolls.get());
        }

        private GooValue roll(AtomicInteger rolls) {
            rolls.incrementAndGet();
            return loot;
        }
    }

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
