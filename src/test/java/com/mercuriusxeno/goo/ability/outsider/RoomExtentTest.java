package com.mercuriusxeno.goo.ability.outsider;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mercuriusxeno.goo.ability.AbilityJson;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers a room's fractional extent: void fed raises it by the fed share of
 * the ring being grown, the same on all four sides, and only blocks lying
 * wholly inside it take a build (decisions
 * outsider-room-grows-fractionally-on-all-sides-at-once and
 * grown-extent-is-the-one-wrap-and-build-boundary).
 */
class RoomExtentTest {

    private static final int BASE = 10;
    private static final int PER_DISTANCE = 1;
    private static final BlockPos CENTER = new BlockPos(1_000_000, OutsiderRooms.ROOM_FLOOR_Y, -2_000_000);
    private static final double EXACT = 1.0E-9;
    private static final int RING_TICKS = 40;

    @Nested
    class Growth {

        private final long ringFive = PlatformSpiral.ringCost(5, BASE, PER_DISTANCE);
        private final long ringSix = PlatformSpiral.ringCost(6, BASE, PER_DISTANCE);

        @Test
        void aFeedShortOfTheRingRaisesTheExtentByTheFedShare() {
            long quarter = ringFive / 4;
            double grown = RoomExtent.grown(RoomExtent.LAID, quarter, BASE, PER_DISTANCE);

            assertEquals(RoomExtent.LAID + quarter / (double) ringFive, grown, EXACT);
        }

        @Test
        void everySideMovesOutByTheSameShare() {
            long quarter = ringFive / 4;
            double share = quarter / (double) ringFive;
            RoomExtent.Planes before = RoomExtent.planesAround(CENTER, RoomExtent.LAID);
            RoomExtent.Planes after = RoomExtent.planesAround(CENTER,
                    RoomExtent.grown(RoomExtent.LAID, quarter, BASE, PER_DISTANCE));

            assertEquals(share, after.east() - before.east(), EXACT, "east");
            assertEquals(share, before.west() - after.west(), EXACT, "west");
            assertEquals(share, after.south() - before.south(), EXACT, "south");
            assertEquals(share, before.north() - after.north(), EXACT, "north");
        }

        @Test
        void feedsAccumulateAcrossAWholeBlockMarkWithNothingLost() {
            long extra = ringSix / 5;
            long total = ringFive + extra;
            double extent = RoomExtent.LAID;
            long fed = 0;
            long step = ringFive / 7 + 1;
            while (fed < total) {
                long feed = Math.min(step, total - fed);
                extent = RoomExtent.grown(extent, feed, BASE, PER_DISTANCE);
                fed += feed;
            }

            assertEquals(5.5 + extra / (double) ringSix, extent, EXACT);
        }

        /**
         * The 9 by 9 is free, so a new room's first growth is ring 5, the blocks past it.
         * decision outsider-room-starts-nine-by-nine
         */
        @Test
        void aNewRoomGrowsThroughRingFiveForRingFivesPrice() {
            assertEquals(ringFive, RoomExtent.priceOfRingGrowing(RoomExtent.LAID, BASE, PER_DISTANCE));
            assertEquals(5.5, RoomExtent.grown(RoomExtent.LAID, ringFive, BASE, PER_DISTANCE), EXACT);
        }

        @Test
        void theFeedFinishesEveryRingInTheRingTime() {
            for (int ring = 2; ring <= 40; ring++) {
                long ringCost = PlatformSpiral.ringCost(ring, BASE, PER_DISTANCE);
                long feed = RoomExtent.feedFor(ring - 0.5, BASE, PER_DISTANCE, RING_TICKS);

                assertTrue(feed * RING_TICKS >= ringCost && (feed - 1) * RING_TICKS < ringCost,
                        "ring " + ring + " costs " + ringCost + ", fed " + feed + " a tick for " + RING_TICKS);
            }
        }
    }

    /**
     * A held second grows one block on every side for the ring's price, read
     * at the params void_outsider.json ships
     * (decision outsider-room-grows-one-block-a-second-on-every-side).
     */
    @Nested
    class Pace {

        private final JsonObject outsider = shippedOutsiderStep();
        private final int ringTicks = outsider.get("ring_ticks").getAsInt();
        private final int base = outsider.get("cell_cost").getAsInt();
        private final int perDistance = outsider.get("distance_cost").getAsInt();

        @Test
        void eachRingTimeGrowsOneBlockForTheRingsPrice() {
            double extent = RoomExtent.LAID;
            long fedThroughRingFive = 0;
            for (int tick = 0; tick < ringTicks; tick++) {
                long feed = RoomExtent.feedFor(extent, base, perDistance, ringTicks);
                fedThroughRingFive += feed;
                extent = RoomExtent.grown(extent, feed, base, perDistance);
            }
            double afterRingFive = extent;
            long fedThroughRingSix = 0;
            for (int tick = 0; tick < ringTicks; tick++) {
                long feed = RoomExtent.feedFor(extent, base, perDistance, ringTicks);
                fedThroughRingSix += feed;
                extent = RoomExtent.grown(extent, feed, base, perDistance);
            }

            assertEquals(5.5, afterRingFive, EXACT, "extent after the first second");
            assertEquals(700, fedThroughRingFive, "void fed through ring 5");
            assertEquals(6.5, extent, EXACT, "extent after the second second");
            assertEquals(912, fedThroughRingSix, "void fed through ring 6");
        }

        private static JsonObject shippedOutsiderStep() {
            JsonObject ability = JsonParser.parseString(AbilityJson.read("void_outsider")).getAsJsonObject();
            return ability.getAsJsonArray("behaviors").get(0).getAsJsonObject();
        }
    }

    @Nested
    class Building {

        /** Ends a quarter of the way into the blocks two out from the center. */
        private static final double PARTWAY = 2.25;

        @ParameterizedTest(name = "toward ({0}, {1})")
        @CsvSource({"1, 0", "-1, 0", "0, 1", "0, -1"})
        void onlyBlocksWhollyInsideTakeABuild(int east, int south) {
            assertTrue(RoomExtent.holdsWholeBlock(PARTWAY, east, south), "the last block wholly inside");
            assertFalse(RoomExtent.holdsWholeBlock(PARTWAY, 2 * east, 2 * south), "the block the boundary cuts");
            assertFalse(RoomExtent.holdsWholeBlock(PARTWAY, 3 * east, 3 * south), "the block beyond");
        }
    }
}
