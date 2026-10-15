package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.program.SiphonRule;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A drink's streams union on the tree its layout fixes: the first runs to
 * the glove, a tributary's path ends on its trunk's centreline at its join,
 * its route runs its own path then the trunk's remainder, and the trunk
 * carries the owner alone before a join and the areas' sum after it,
 * swelling in over the merge length and bulging into a node at the join; the
 * liquid's pace is the base pace times the square root of the goo massing
 * where it flows, so a trunk fed by two runs faster than either, and the
 * head, tail and material follow that pace (decision unmake-waves-dissolve-by-crucible-cost).
 */
class DrinkTreeTest {

    private static final double DELTA = 1e-9;
    private static final Vec3 GLOVE = new Vec3(1.5, 2.2, 3.1);
    private static final long START = 100;
    /** A drain long enough that no tail has left the block while the tests look. */
    private static final long END = 1000;
    private static final double NOW = 100;
    /** A time every block's goo weighs wholly on the pace. */
    private static final double RAMPED = START + DrinkTree.MASS_RAMP;
    private static final DrinkTree.Block NEAR = block(new BlockPos(7, 2, 3), 1);
    private static final DrinkTree.Block FAR = block(new BlockPos(7, 3, 4), 1);
    /** Blocks past the join and its merge at which both liquids are read on the trunk. */
    private static final double PAST_THE_MERGE = 0.3;
    private static final long THOUSAND = 1000;
    private static final long QUARTER = 250;
    private static final long FOUR_THOUSAND = 4000;
    private static final double TICKS_LATER = 25;
    private static final double NINE = 9;
    private static final double THREE = 3;
    private static final double TWO = 2;
    /** Blocks between samples along a route. */
    private static final double SAMPLE = 0.05;
    /** Blocks a widest radius read at samples a step apart may miss the spout's own by. */
    private static final double SAMPLE_WIDTH = 0.03;
    /** Blocks a point counts as on a spine within. */
    private static final double ON_THE_SPINE = 1e-6;

    /** The time the blocks were picked, the square leaving the hand. */
    private static final long PICKED = START - SiphonRule.INJECT_TICKS;
    private static final double HALF_WAY = 0.5;

    private static DrinkTree.Block block(BlockPos pos, double scale) {
        return new DrinkTree.Block(pos, Vec3.atCenterOf(pos), scale, PICKED, START, END);
    }

    /** The trunk's arrival into the hand: flowing west, as a look east pulls it. */
    private static final Vec3 PULL = new Vec3(-1, 0, 0);

    private static DrinkLayout layoutOf(DrinkTree.Block... blocks) {
        DrinkLayout layout = new DrinkLayout();
        layout.place(List.of(blocks).stream().map(DrinkTree.Block::pos).toList(), GLOVE);
        return layout;
    }

    private static List<DrinkTree.Stream> treeAt(double now) {
        return DrinkTree.build(List.of(FAR, NEAR), layoutOf(FAR, NEAR), GLOVE, PULL, now);
    }

    private static List<DrinkTree.Stream> tree() {
        return treeAt(NOW);
    }

    private static DrinkTree.Stream loneAt(double now) {
        return DrinkTree.build(List.of(NEAR), layoutOf(NEAR), GLOVE, PULL, now).getFirst();
    }

    private static DrinkTree.Stream lone() {
        return loneAt(NOW);
    }

    /**
     * @param trunk the trunk
     * @param blocksPastJoin blocks past the tributary's join along the trunk
     * @return the share of the trunk's path there
     */
    private static double pastJoin(DrinkTree.Stream trunk, double blocksPastJoin) {
        return trunk.tributaries().getFirst().joinShare() + blocksPastJoin / trunk.path().length();
    }

    @Nested
    class Flowing {

        @Test
        void aStreamingBlockNotYetBuiltFlowsEverywhereAGoneBlockNowhereAndABuiltOneWhereItsStreamDoes() {
            BlockPos fresh = new BlockPos(7, 4, 5);
            BlockPos gone = new BlockPos(7, 5, 6);
            DrinkTree.Stream flowing = lone();
            DrinkTree.Stream spent = DrinkTree.build(List.of(FAR), layoutOf(FAR), GLOVE, PULL, END + THOUSAND)
                    .getFirst();
            DrinkLayout.Flowing test = DrinkTree.flowingOf(Map.of(NEAR.pos(), flowing, FAR.pos(), spent),
                    Set.of(NEAR.pos(), FAR.pos(), fresh));

            assertTrue(flowing.flowingAt(0) && test.at(NEAR.pos(), 0), "a built stream flows where it does");
            assertFalse(spent.flowingAt(1) || test.at(FAR.pos(), 1), "and not where it has passed");
            assertTrue(test.at(fresh, 0) && test.at(fresh, 1),
                    "a block streaming this frame but not yet built flows everywhere, so the first frame's blocks join");
            assertFalse(test.at(gone, 0), "a block no longer streaming flows nowhere, so its tributaries re-root");
        }

        @Test
        void aTrunkWhoseOwnLiquidHasPassedStillCarriesWhatJoinsItSoItsTributariesStay() {
            double passed = END;
            List<DrinkTree.Stream> streams = treeAt(passed);
            while (streams.getFirst().flowingAt(streams.get(1).joinShare() * streams.getFirst().path().length())) {
                passed += 1;
                streams = treeAt(passed);
            }
            DrinkTree.Stream trunk = streams.getFirst();
            DrinkTree.Stream tributary = streams.get(1);
            double joinAt = tributary.joinShare() * trunk.path().length();
            DrinkLayout.Flowing test = DrinkTree.flowingOf(Map.of(NEAR.pos(), trunk, FAR.pos(), tributary),
                    Set.of(NEAR.pos(), FAR.pos()));

            assertFalse(trunk.flowingAt(joinAt), "the trunk's own tail is past the join");
            assertTrue(tributary.flowingAt(tributary.path().length()), "the tributary's liquid still reaches its join");
            assertTrue(trunk.carriesAt(joinAt), "so the trunk still carries at the join");
            assertFalse(trunk.carriesAt(joinAt / TWO), "and nothing before it, where no liquid is left");
            assertTrue(test.at(NEAR.pos(), joinAt), "the layout keeps the tributary on its trunk");
            assertFalse(treeAt(END + THOUSAND).getFirst().carriesAt(joinAt), "once nothing flows it carries nothing");
        }

        @Test
        void aTrunkCarriesATributaryWhoseLiquidIsRidingItThoughItHasLeftItsOwnPath() {
            double riding = END;
            List<DrinkTree.Stream> streams = treeAt(riding);
            while (streams.get(1).flowingAt(streams.get(1).path().length())) {
                riding += 1;
                streams = treeAt(riding);
            }
            DrinkTree.Stream trunk = streams.getFirst();
            DrinkTree.Stream tributary = streams.get(1);
            double joinAt = tributary.joinShare() * trunk.path().length();

            assertFalse(tributary.flowingAt(tributary.path().length()), "the tributary's tail has left its own path");
            assertFalse(tributary.spent(), "its liquid is still in flight on the trunk");
            assertFalse(trunk.flowingAt(joinAt), "the trunk's own liquid is gone");
            assertTrue(trunk.carriesAt(joinAt), "so the trunk still carries it and the tributary is not torn off");
        }
    }

    @Nested
    class Shape {

        @Test
        void aLoneStreamRunsFromItsLayoutsFarSideToTheGloveArrivingAlongThePull() {
            DrinkLayout layout = layoutOf(NEAR);
            List<DrinkTree.Stream> streams = DrinkTree.build(List.of(NEAR), layout, GLOVE, PULL, NOW);

            assertEquals(1, streams.size());
            DrinkTree.Stream stream = streams.getFirst();
            assertNull(stream.trunk());
            assertEquals(GLOVE, stream.path().to());
            assertEquals(layout.node(NEAR.pos()).farSide(), stream.path().from());
            assertEquals(PULL, stream.path().arrival());
            assertEquals(NOW, stream.now(), DELTA);
        }

        @Test
        void aTributarysPathEndsOnItsTrunksCentrelineAtItsJoin() {
            List<DrinkTree.Stream> streams = tree();
            DrinkTree.Stream trunk = streams.getFirst();
            DrinkTree.Stream tributary = streams.get(1);
            double joinAt = layoutOf(FAR, NEAR).node(FAR.pos()).joinAt();

            assertSame(NEAR, trunk.block());
            assertSame(trunk, tributary.trunk());
            assertEquals(List.of(tributary), trunk.tributaries());
            assertEquals(joinAt / trunk.path().length(), tributary.joinShare(), DELTA);
            assertEquals(0, tributary.path().to().distanceTo(DrinkStream.pointAt(trunk.path(), tributary.joinShare(),
                    NOW)), DELTA);
            assertEquals(DrinkStream.flowAt(trunk.path(), tributary.joinShare(), NOW), tributary.path().arrival());
            assertSame(trunk.path(), tributary.path().trunk(), "its last stretch slides onto the trunk's spine");
            assertEquals(tributary.joinShare(), tributary.path().joinShare(), DELTA);
        }

        @Test
        void aTributarysRouteRunsItsPathThenTheTrunksRemainder() {
            List<DrinkTree.Stream> streams = tree();
            DrinkTree.Stream trunk = streams.getFirst();
            DrinkTree.Stream tributary = streams.get(1);

            assertEquals(trunk.path().length(), trunk.routeLength(), DELTA);
            assertEquals(tributary.path().length() + (1 - tributary.joinShare()) * trunk.path().length(),
                    tributary.routeLength(), DELTA);
        }

        @Test
        void theScaleIsTheSquareRootOfTheVolumeOverAThousand() {
            assertEquals(1, DrinkTree.scaleOf(THOUSAND), DELTA);
            assertEquals(0.5, DrinkTree.scaleOf(QUARTER), DELTA);
            assertEquals(2, DrinkTree.scaleOf(FOUR_THOUSAND), DELTA);
        }
    }

    @Nested
    class Square {

        @Test
        void theBlockStandsAsItselfWithNoStreamAndNoMassUntilTheSquareLands() {
            DrinkTree.Stream leaving = loneAt(PICKED);
            DrinkTree.Stream landing = loneAt(START - DELTA);
            DrinkTree.Stream started = loneAt(START);
            double middle = DrinkBody.CENTER / started.path().length();

            assertTrue(leaving.awaiting());
            assertTrue(landing.awaiting());
            assertFalse(started.awaiting());
            assertEquals(0, DrinkTree.contribution(landing, landing, middle), DELTA,
                    "nothing of the stream shows while the square flies, not even the block's own liquid");
            assertEquals(0, DrinkTree.ring(landing, middle).radius(), DELTA);
            assertNull(DrinkTree.skeleton(landing).box());
            assertEquals(0, landing.block().massAt(landing.now()), DELTA);
            assertTrue(landing.flowingAt(0) && landing.flowingAt(landing.routeLength()), "the layout keeps its route");
            assertEquals(DrinkBody.MOUTH, DrinkTree.contribution(started, started, middle), DELTA,
                    "the block's liquid shows as the square lands");
        }

        @Test
        void theSquaresFlightRunsFromThePickToTheStart() {
            DrinkTree.Block block = NEAR;

            assertEquals(0, block.flightShareAt(PICKED), DELTA);
            assertEquals(HALF_WAY, block.flightShareAt(PICKED + SiphonRule.INJECT_TICKS * HALF_WAY), DELTA);
            assertEquals(1, block.flightShareAt(START), DELTA);
        }
    }

    @Nested
    class Pace {

        @Test
        void aBlocksGooWeighsInOverTheRampFromItsStartByItsArea() {
            assertEquals(0, NEAR.massAt(START), DELTA);
            assertEquals(0.5, NEAR.massAt(START + DrinkTree.MASS_RAMP / 2), DELTA);
            assertEquals(1, NEAR.massAt(RAMPED), DELTA);
            assertEquals(1, NEAR.massAt(RAMPED + TICKS_LATER), DELTA);
            assertEquals(TWO * TWO, block(NEAR.pos(), TWO).massAt(RAMPED), DELTA);
        }

        @Test
        void aLoneStreamRunsAtTheBasePaceAndATrunkFedByTwoFasterByTheRootOfTheirGoo() {
            List<DrinkTree.Stream> streams = treeAt(RAMPED);
            DrinkTree.Stream trunk = streams.getFirst();
            DrinkTree.Stream tributary = streams.get(1);
            double before = pastJoin(trunk, -DrinkTree.SWELL_FROM);
            double after = pastJoin(trunk, PAST_THE_MERGE);

            assertEquals(DrinkStream.FLOW, loneAt(START).speedAt(0.5), DELTA);
            assertEquals(DrinkStream.FLOW, loneAt(RAMPED).speedAt(0.5), DELTA);
            assertEquals(DrinkStream.FLOW, tributary.speedAt(0.5), DELTA);
            assertEquals(1, trunk.massAt(before), DELTA);
            assertEquals(1.5, trunk.massAt(pastJoin(trunk, -DrinkStream.RIDE - DrinkTree.MERGE / TWO)), DELTA,
                    "half swollen in halfway through the swell");
            assertEquals(TWO, trunk.massAt(pastJoin(trunk, -DrinkStream.RIDE)), DELTA,
                    "wholly swollen in where the tributary's ride begins");
            assertEquals(TWO, trunk.massAt(after), DELTA);
            assertEquals(DrinkStream.FLOW * Math.sqrt(TWO), trunk.speedAt(after), DELTA);
        }

        @Test
        void theTravelTimeSumsTheRouteOverItsPaceAndInvertsBackToTheDistance() {
            DrinkTree.Stream alone = loneAt(RAMPED);
            List<DrinkTree.Stream> streams = treeAt(RAMPED);
            DrinkTree.Stream trunk = streams.getFirst();
            DrinkTree.Stream tributary = streams.get(1);
            double length = alone.path().length();
            double joinDistance = tributary.joinShare() * trunk.path().length();

            assertEquals(length / DrinkStream.FLOW, alone.timeTo(length), 1e-6);
            assertEquals(0.3, alone.distanceAt(alone.timeTo(0.3)), 1e-6);
            assertEquals(length + 1, alone.distanceAt(alone.timeTo(length + 1)), 1e-6);
            assertEquals(-1 / DrinkStream.FLOW, alone.timeTo(-1), DELTA);
            assertEquals(tributary.timeTo(tributary.path().length()) + trunk.timeTo(trunk.path().length())
                    - trunk.timeTo(joinDistance), tributary.timeTo(tributary.routeLength()), 1e-6);
            assertTrue(tributary.timeTo(tributary.routeLength()) < tributary.routeLength() / DrinkStream.FLOW,
                    "the trunk carries the tributary's liquid on faster than the base pace");
            assertEquals(tributary.routeLength(), tributary.distanceAt(tributary.timeTo(tributary.routeLength())),
                    1e-6);
        }

        @Test
        void theHeadStartsATipOutOfTheBlockAndTheTailLeavesItsFaceAtThePace() {
            double face = DrinkStream.BLOCK_SPAN;
            double tip = face + DrinkStream.TIP;

            assertEquals(tip, loneAt(START).headAt(), 1e-6);
            assertEquals(tip + TICKS_LATER * DrinkStream.FLOW, loneAt(START + TICKS_LATER).headAt(), 1e-6);
            assertTrue(loneAt(START).tailAt() < face, "the block still feeds the stream");
            assertEquals(face, loneAt(END).tailAt(), 1e-6);
            assertEquals(face + TICKS_LATER * DrinkStream.FLOW, loneAt(END + TICKS_LATER).tailAt(), 1e-6);
        }

        @Test
        void aStreamFlowsAtADistanceUntilItsTailPassesAndIsSpentOnceAllOfItIsPast() {
            DrinkTree.Stream draining = loneAt(END);
            DrinkTree.Stream awaiting = loneAt(PICKED + 1);
            DrinkTree.Stream longGone = loneAt(END + THOUSAND);

            assertTrue(draining.flowingAt(DrinkStream.BLOCK_SPAN + 1), "the liquid is still coming past the face");
            assertFalse(draining.flowingAt(DrinkStream.BLOCK_SPAN / 2), "the far half of the block has drained");
            assertTrue(awaiting.flowingAt(0) && awaiting.flowingAt(awaiting.routeLength()));
            assertFalse(draining.spent());
            assertTrue(longGone.spent());
            assertTrue(treeAt(END + THOUSAND).getFirst().spent(), "a trunk is spent once its tributaries are too");
            assertFalse(treeAt(END).getFirst().spent());
        }

        @Test
        void theMaterialFallsAtTheBasePaceWhereTheLiquidFlowsAtIt() {
            DrinkTree.Stream before = loneAt(RAMPED);
            DrinkTree.Stream after = loneAt(RAMPED + 1);
            double length = before.path().length();

            assertEquals(-DrinkStream.FLOW, after.materialAt(0.5) - before.materialAt(0.5), DELTA);
            assertEquals(length, before.materialAt(length) - before.materialAt(0), 1e-6);
        }
    }

    @Nested
    class Width {

        /** decision unmake-stream-is-a-shrinking-waterspout */
        @Test
        void theTrunkCarriesNothingOfATributaryBeforeItsSwellAndTheRootOfTheCountOfEachOwnWidthAfterIt() {
            List<DrinkTree.Stream> streams = treeAt(START + tree().get(1).routeLength() / DrinkStream.FLOW);
            DrinkTree.Stream trunk = streams.getFirst();
            DrinkTree.Stream tributary = streams.get(1);
            double before = pastJoin(trunk, -DrinkTree.SWELL_FROM - PAST_THE_MERGE);
            double after = pastJoin(trunk, PAST_THE_MERGE);

            assertEquals(0, DrinkTree.contribution(tributary, trunk, before), DELTA);
            DrinkTree.Flow owner = DrinkTree.flowOf(trunk, trunk, after);
            DrinkTree.Flow joined = DrinkTree.flowOf(tributary, trunk, after);
            assertTrue(joined.power() > 0, "the tributary's liquid is on the trunk");
            assertEquals(1, joined.presence(), DELTA, "wholly swollen in past the join");
            assertTrue(joined.width() < owner.width() / Math.pow(owner.presence(), 1 / DrinkTree.TRUNK_ROOT),
                    "the tributary's liquid runs on as narrow as its own spout has made it, narrower than the trunk's own");
            assertEquals(owner.plus(joined).width(), DrinkTree.ring(trunk, after).radius(), DELTA,
                    "the fourth root of the sum of each stream's own width to the fourth");
        }

        @Test
        void aTrunkGrowsByTheFourthRootOfTheStreamsThroughItEachCountingByItsPresence() {
            double one = 0.15;
            DrinkTree.Flow nine = DrinkTree.Flow.of(one, NINE);
            DrinkTree.Flow half = DrinkTree.Flow.of(one, 0.5);
            DrinkTree.Flow oneAndHalf = DrinkTree.Flow.of(one, 1).plus(half);

            assertEquals(4, DrinkTree.TRUNK_ROOT, DELTA);
            assertEquals(Math.sqrt(THREE) * one, nine.width(), DELTA, "nine equal streams make root three times one");
            assertEquals(one, DrinkTree.Flow.of(one, 1).plus(DrinkTree.Flow.of(0, 0)).width(), DELTA);
            assertEquals(one * Math.pow(1.5, 1 / DrinkTree.TRUNK_ROOT), oneAndHalf.width(), DELTA,
                    "half a stream joining widens the trunk by the fourth root of one and a half");
            assertEquals(one * Math.pow(0.5, 1 / DrinkTree.TRUNK_ROOT), half.width(), DELTA,
                    "a trunk carrying only half a stream is as wide as the fourth root of a half");
            assertTrue(DrinkTree.Flow.of(one, TWO * NINE * NINE).width() < 4 * one,
                    "even 162 streams make under four times one");
        }

        /** decision unmake-stream-is-a-shrinking-waterspout */
        @Test
        void aWideStreamAmongNarrowOnesLeadsTheTrunksWidthRatherThanDrowningInTheirMean() {
            double narrow = 0.15;
            double wide = DrinkBody.MOUTH;
            DrinkTree.Flow many = DrinkTree.Flow.of(narrow, NINE * THREE);
            DrinkTree.Flow withBlock = many.plus(DrinkTree.Flow.of(wide, 1));

            assertTrue(withBlock.width() >= wide, "a block's own mouth fits inside the trunk it melts into: "
                    + withBlock.width());
            assertTrue((NINE * THREE * narrow + wide) / (NINE * THREE + 1) * Math.pow(NINE * THREE + 1, 0.25) < wide,
                    "where the mean of the widths left the trunk narrower than the block's mouth");
        }

        private double widthOf(DrinkTree.Flow flow) {
            return flow.width();
        }

        /** decision unmake-stream-is-a-shrinking-waterspout */
        @Test
        void aTributarysLastStretchLiesOnItsTrunksSpineRatherThanCirclingIt() {
            List<DrinkTree.Stream> streams = tree();
            DrinkTree.Stream trunk = streams.getFirst();
            DrinkTree.Stream tributary = streams.get(1);
            double length = tributary.path().length();
            double ride = DrinkStream.RIDE;

            for (double before = 0; before <= ride; before += SAMPLE) {
                Vec3 own = DrinkStream.pointAt(tributary.path(), 1 - before / length, NOW);
                Vec3 spine = DrinkStream.pointAt(trunk.path(), tributary.joinShare() - before / trunk.path().length(),
                        NOW);
                assertEquals(0, own.distanceTo(spine), ON_THE_SPINE, before + " blocks before its join the "
                        + "tributary runs on its trunk's spine");
            }
        }

        @Test
        void aTributaryHandsItsWidthToTheTrunkOverTheRideBeforeItsJoinSoTheJoinIsOneNeck() {
            List<DrinkTree.Stream> streams = treeAt(START + tree().get(1).routeLength() / DrinkStream.FLOW);
            DrinkTree.Stream trunk = streams.getFirst();
            DrinkTree.Stream tributary = streams.get(1);
            double ride = DrinkStream.RIDE;
            double length = tributary.path().length();

            assertEquals(DrinkTree.MERGE, ride, DELTA, "the trunk swells over the same ride");
            assertEquals(1, DrinkTree.handoverAt(trunk, 1), DELTA, "a stream running to the glove keeps its width");
            assertEquals(1, DrinkTree.handoverAt(tributary, 1 - ride / length), DELTA, "all of it a ride before");
            assertEquals(0.5, DrinkTree.handoverAt(tributary, 1 - ride / TWO / length), DELTA, "half midway");
            assertEquals(0, DrinkTree.handoverAt(tributary, 1), DELTA, "none at the join");
            assertEquals(0, DrinkTree.ring(tributary, 1).radius(), DELTA, "so its last ring has no width of its own");
            assertTrue(DrinkTree.ring(tributary, 1 - ride / length).radius() > 0);
        }

        /** decision unmake-stream-is-a-shrinking-waterspout */
        @Test
        void aStreamIsWidestLeavingItsBlockAndNarrowsOverItsWholeRouteToAHairHeldIntoThePalm() {
            DrinkTree.Stream stream = loneAt(START + (lone().routeLength() + DrinkStream.BLOCK_SPAN
                    + DrinkStream.TIP) / DrinkStream.FLOW + TICKS_LATER);
            double length = stream.path().length();
            double full = DrinkBody.MOUTH;
            double leaving = DrinkBody.CENTER / length;
            double step = SAMPLE / length;
            double threadFrom = DrinkTree.threadFrom(stream) / length;

            assertEquals(full, DrinkTree.radiusAt(stream, leaving), DELTA, "the full width where it leaves the block");
            double before = DrinkTree.radiusAt(stream, leaving);
            for (double share = leaving + step; share <= 1; share += step) {
                double radius = DrinkTree.radiusAt(stream, share);
                assertTrue(radius <= before + DELTA, "never wider down the route: " + radius + " after " + before
                        + " at " + share * length + " blocks");
                assertTrue(radius < full, "no stretch held at the full width past the block");
                before = radius;
            }
            for (double share = threadFrom; share <= 1; share += step) {
                assertEquals(DrinkTree.THREAD, DrinkTree.radiusAt(stream, share), DELTA, "the hair into the palm");
            }
            assertEquals(stream.routeLength() - DrinkTree.THREAD_RUN, DrinkTree.threadFrom(stream), DELTA,
                    "the hair from half a block before the glove");
            assertEquals(0.5, DrinkTree.THREAD_RUN, DELTA);
            assertEquals(DrinkStream.THINNEST, DrinkTree.THREAD, DELTA, "the hair is the thinnest the skin reads round");
        }

        /**
         * @return ticks after its start a lone stream's head has run past the glove
         */
        private double flowingTicks() {
            return (lone().routeLength() + DrinkStream.BLOCK_SPAN + DrinkStream.TIP) / DrinkStream.FLOW + TICKS_LATER;
        }

        /**
         * @param stream a stream
         * @return its widest radius along its whole path, read at samples a short step apart
         */
        private double widestOf(DrinkTree.Stream stream) {
            double widest = 0;
            for (double share = 0; share <= 1; share += SAMPLE / stream.path().length()) {
                widest = Math.max(widest, DrinkTree.radiusAt(stream, share));
            }
            return widest;
        }

        /** decisions unmake-waves-dissolve-by-crucible-cost and unmake-stream-is-a-shrinking-waterspout */
        @Test
        void aDrainedBlocksLiquidKeepsTheWaterspoutsWidthWhereItStandsAllTheWayIntoTheHand() {
            DrinkTree.Stream flowing = loneAt(START + flowingTicks());
            double length = flowing.path().length();

            assertEquals(DrinkBody.MOUTH, widestOf(flowing), SAMPLE_WIDTH, "the whole mouth while the block drains");
            for (double later = END; loneAt(later).tailAt() < loneAt(later).routeLength(); later += 1) {
                DrinkTree.Stream drained = loneAt(later);
                double behind = drained.tailAt() + DrinkStream.TIP;
                for (double share = behind / length; share < 1; share += SAMPLE / length) {
                    double distance = share * length;
                    double waterspout = DrinkTree.THREAD + (DrinkBody.MOUTH - DrinkTree.THREAD)
                            * DrinkTree.spoutAlong(drained, distance);
                    assertEquals(waterspout, DrinkTree.radiusAt(drained, share), DELTA, (later - END)
                            + " ticks after the drain the liquid at " + distance + " blocks is as wide as the "
                            + "waterspout is there, not shrunk as its tail flows");
                }
                assertFalse(drained.spent(), "the stream is drawn until its tail is in the hand");
            }
        }

        /** decision unmake-waves-dissolve-by-crucible-cost */
        @Test
        void aDrainedTributarysTailRidesItsTrunkAllTheWayIntoTheHand() {
            for (double later = END; treeAt(later).get(1).tailAt() < treeAt(later).get(1).routeLength(); later += 1) {
                List<DrinkTree.Stream> streams = treeAt(later);
                DrinkTree.Stream trunk = streams.getFirst();
                DrinkTree.Stream tributary = streams.get(1);

                assertFalse(tributary.spent(), "the tributary is drawn until its tail is in the hand");
                assertFalse(trunk.spent(), "its trunk carries it until then");
                assertTrue(trunk.carriesAt(trunk.path().length()), (later - END) + " ticks after the drain the "
                        + "trunk still carries liquid into the hand, the tributary's tail at " + tributary.tailAt());
            }
        }

        /** decision unmake-stream-is-a-shrinking-waterspout */
        @Test
        void theSpoutNarrowsFastestByItsBlockAndEasesIntoTheHairWithNoCliff() {
            DrinkTree.Stream stream = lone();
            double length = stream.path().length();
            double leaving = DrinkBody.CENTER / length;
            double threadFrom = DrinkTree.threadFrom(stream) / length;
            double halfway = (leaving + threadFrom) / TWO;

            assertEquals(1, DrinkTree.spoutAt(stream, leaving), DELTA);
            assertEquals(0.25, DrinkTree.spoutAt(stream, halfway), DELTA, "a quarter of the width halfway");
            assertEquals(0, DrinkTree.spoutAt(stream, threadFrom), DELTA);
            double nearEnd = DrinkTree.spoutAt(stream, threadFrom - SAMPLE / length);
            assertTrue(nearEnd < SAMPLE, "the fall eases into the hair rather than dropping onto it: " + nearEnd);
        }

        @Test
        void aBlockRightByTheHandStillNarrowsToTheHairOverHalfWhatItsRouteLeaves() {
            DrinkTree.Block beside = block(new BlockPos(1, 2, 3), 1);
            DrinkTree.Stream stream = DrinkTree.build(List.of(beside), layoutOf(beside), GLOVE, PULL, NOW).getFirst();
            double route = stream.routeLength();

            assertTrue(route - DrinkTree.THREAD_RUN < (DrinkBody.CENTER + route) / TWO, "too short a route for the run");
            assertEquals((DrinkBody.CENTER + route) / TWO, DrinkTree.threadFrom(stream), DELTA,
                    "so the hair starts halfway from the block's middle to the glove");
        }

        @Test
        void theTrunkSwellsOverTheMergeBeforeTheRideSoItIsWholeWhereTheTributaryHandsOver() {
            double ride = DrinkStream.RIDE;

            assertEquals(ride + DrinkTree.MERGE, DrinkTree.SWELL_FROM, DELTA);
            assertEquals(0, DrinkTree.mergeRamp(-DrinkTree.SWELL_FROM), DELTA);
            assertEquals(0.5, DrinkTree.mergeRamp(-ride - DrinkTree.MERGE / TWO), DELTA);
            assertEquals(1, DrinkTree.mergeRamp(-ride), DELTA, "whole where the ride begins");
            assertEquals(1, DrinkTree.mergeRamp(0), DELTA);
            assertEquals(0, DrinkTree.mergeRamp(-DrinkTree.SWELL_FROM * 2), DELTA);
        }

        @Test
        void aTrunkWhoseOwnLiquidHasPassedIsStillAsWideAsWhatFlowsThroughIt() {
            List<DrinkTree.Stream> streams = treeAt(START + tree().get(1).routeLength() / DrinkStream.FLOW);
            DrinkTree.Stream trunk = streams.getFirst();
            DrinkTree.Stream tributary = streams.get(1);
            double join = tributary.joinShare();
            DrinkTree.Flow joined = DrinkTree.flowOf(tributary, trunk, join);

            assertTrue(joined.power() > 0, "the tributary's liquid has arrived at its join");
            assertEquals(1, joined.presence(), DELTA, "wholly swollen in at the join itself");
            assertTrue(DrinkTree.radiusAt(trunk, join) >= widthOf(joined) - DELTA,
                    "the trunk at the join is at least as wide as the joining stream alone would make it");
        }

        @Test
        void theBlockIsACubeOfLiquidWhereItStoodAtTheStartAndGoneWhenDrained() {
            DrinkTree.Stream starting = loneAt(START);
            DrinkTree.Stream drained = loneAt(END);
            double middle = DrinkBody.CENTER / starting.path().length();
            DrinkField.Skeleton startingSkeleton = DrinkTree.skeleton(starting);

            assertEquals(DrinkBody.MOUTH, DrinkTree.ring(starting, middle).radius(), DELTA);
            assertEquals(NEAR.center(), startingSkeleton.box().center());
            assertEquals(DrinkBody.MOUTH, startingSkeleton.box().half(), DELTA);
            assertEquals(0, startingSkeleton.box().rounding(), DELTA);
            assertNull(DrinkTree.skeleton(drained).box());
            assertEquals(DrinkTree.rings(starting).size(), startingSkeleton.rings().size());
        }

        @Test
        void aTributarysLiquidReachesTheTrunkOnlyAfterFlowingItsOwnPath() {
            DrinkTree.Stream tributary = tree().get(1);
            double ownPath = tributary.path().length() - DrinkStream.BLOCK_SPAN - DrinkStream.TIP;
            double onTrunk = ownPath + DrinkTree.MERGE + DrinkStream.TIP + DrinkStream.BLOCK_SPAN;
            List<DrinkTree.Stream> early = treeAt(START + ownPath / DrinkStream.FLOW);
            List<DrinkTree.Stream> late = treeAt(START + onTrunk / DrinkStream.FLOW);
            double share = pastJoin(early.getFirst(), DrinkTree.MERGE);

            assertEquals(0, DrinkTree.contribution(early.get(1), early.getFirst(), share), DELTA);
            assertTrue(DrinkTree.contribution(late.get(1), late.getFirst(), share) > 0);
        }

        @Test
        void aPathsRingsRunFromTheBlocksFarSideToItsEndAtThePace() {
            DrinkTree.Stream stream = lone();
            List<DrinkStream.Ring> rings = DrinkTree.rings(stream);
            DrinkStream.Ring ring = DrinkTree.ring(stream, 0.5);
            double distance = 0.5 * stream.path().length();

            assertEquals(0, rings.getFirst().share(), DELTA);
            assertEquals(1, rings.getLast().share(), DELTA);
            assertTrue(rings.size() > DrinkStream.RINGS_PER_BLOCK, "ten rings to the block");
            assertEquals(0.5, ring.share(), DELTA);
            assertEquals(stream.materialAt(distance), ring.material(), DELTA);
            assertEquals(stream.speedAt(0.5), ring.speed(), DELTA);
        }

        @Test
        void aPathsRingsMaterialIsBroughtWithinATexturePeriodOfZeroTogether() {
            DrinkTree.Stream stream = lone();
            List<DrinkStream.Ring> rings = DrinkTree.rings(stream);
            double period = DrinkStream.TEXTURE_PERIOD;
            double first = rings.getFirst().material();
            double shift = stream.materialAt(0) - first;
            double step = DrinkTree.ring(stream, 1.0 / (rings.size() - 1)).material() - stream.materialAt(0);

            assertTrue(first >= 0 && first < period, "the first ring lies within a period of zero");
            assertEquals(shift, period * Math.round(shift / period), DELTA, "shifted by whole periods");
            assertEquals(step, rings.get(1).material() - first, DELTA, "the rings keep their spacing");
        }

        @Test
        void theTravelTimeReadsCleanlyAtAndJustShortOfThePathsEnd() {
            DrinkTree.Stream stream = loneAt(RAMPED);
            double length = stream.path().length();
            double shy = Math.nextDown(length);

            assertEquals(stream.timeTo(length), stream.timeTo(shy), 1e-9);
            assertEquals(length / DrinkStream.FLOW, stream.timeTo(shy), 1e-6);
        }
    }
}
