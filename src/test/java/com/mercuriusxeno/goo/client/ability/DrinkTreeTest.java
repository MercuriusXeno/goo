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
            double before = pastJoin(trunk, -DrinkTree.MERGE - PAST_THE_MERGE);
            double after = pastJoin(trunk, DrinkTree.MERGE + PAST_THE_MERGE);

            assertEquals(DrinkStream.FLOW, loneAt(START).speedAt(0.5), DELTA);
            assertEquals(DrinkStream.FLOW, loneAt(RAMPED).speedAt(0.5), DELTA);
            assertEquals(DrinkStream.FLOW, tributary.speedAt(0.5), DELTA);
            assertEquals(1, trunk.massAt(before), DELTA);
            assertEquals(TWO, trunk.massAt(after), DELTA);
            assertEquals(1.5, trunk.massAt(tributary.joinShare()), DELTA);
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

        @Test
        void theTrunkCarriesTheOwnerAloneBeforeTheJoinAndTheRootOfTheCountAfterIt() {
            List<DrinkTree.Stream> streams = treeAt(START + tree().get(1).routeLength() / DrinkStream.FLOW);
            DrinkTree.Stream trunk = streams.getFirst();
            DrinkTree.Stream tributary = streams.get(1);
            double before = pastJoin(trunk, -DrinkTree.MERGE - PAST_THE_MERGE);
            double after = pastJoin(trunk, DrinkTree.MERGE + PAST_THE_MERGE);

            assertEquals(0, DrinkTree.contribution(tributary, trunk, before), DELTA);
            assertEquals(DrinkTree.contribution(trunk, trunk, before), DrinkTree.ring(trunk, before).radius(), DELTA);
            DrinkTree.Flow owner = DrinkTree.flowOf(trunk, trunk, after);
            DrinkTree.Flow joined = DrinkTree.flowOf(tributary, trunk, after);
            assertTrue(joined.radius() > 0, "the tributary's liquid is on the trunk");
            assertEquals(1, joined.presence(), DELTA, "wholly swollen in past the merge");
            double presence = owner.presence() + joined.presence();
            assertEquals((owner.radius() + joined.radius()) / presence * Math.pow(presence, 1 / DrinkTree.TRUNK_ROOT),
                    DrinkTree.ring(trunk, after).radius(), DELTA);
        }

        @Test
        void aTrunkGrowsByTheFourthRootOfTheStreamsThroughItEachCountingByItsPresence() {
            double one = 0.15;
            DrinkTree.Flow nine = new DrinkTree.Flow(NINE * one, NINE);
            DrinkTree.Flow half = new DrinkTree.Flow(one * 0.5, 0.5);
            DrinkTree.Flow oneAndHalf = new DrinkTree.Flow(one, 1).plus(half);

            assertEquals(4, DrinkTree.TRUNK_ROOT, DELTA);
            assertEquals(Math.sqrt(THREE) * one, widthOf(nine), DELTA, "nine equal streams make root three times one");
            assertEquals(one, widthOf(new DrinkTree.Flow(one, 1).plus(new DrinkTree.Flow(0, 0))), DELTA);
            assertEquals(one * Math.pow(1.5, 1 / DrinkTree.TRUNK_ROOT), widthOf(oneAndHalf), DELTA,
                    "half a stream joining widens the trunk by the fourth root of one and a half");
            assertEquals(one * Math.pow(0.5, 1 / DrinkTree.TRUNK_ROOT), widthOf(half), DELTA,
                    "a trunk carrying only half a stream is as wide as the fourth root of a half");
            assertTrue(widthOf(new DrinkTree.Flow(TWO * NINE * NINE * one, TWO * NINE * NINE)) < 4 * one,
                    "even 162 streams make under four times one");
        }

        private double widthOf(DrinkTree.Flow flow) {
            return flow.radius() / flow.presence() * Math.pow(flow.presence(), 1 / DrinkTree.TRUNK_ROOT);
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

        @Test
        void everyStreamFallsStraightToAThreadOverTheLastTwoFifthsOfItsRoute() {
            List<DrinkTree.Stream> streams = treeAt(START + tree().get(1).routeLength() / DrinkStream.FLOW);
            DrinkTree.Stream trunk = streams.getFirst();
            DrinkTree.Stream tributary = streams.get(1);
            double taperFrom = DrinkTree.TAPER_FROM;
            double midway = (1 + taperFrom) / TWO;
            DrinkTree.Flow full = DrinkTree.flowOf(trunk, trunk, taperFrom).plus(DrinkTree.flowOf(tributary, trunk,
                    taperFrom));
            DrinkTree.Flow mid = DrinkTree.flowOf(trunk, trunk, midway).plus(DrinkTree.flowOf(tributary, trunk, midway));
            double joinShare = tributary.path().length() / tributary.routeLength();

            assertEquals(0.6, taperFrom, DELTA, "the taper starts three fifths of the way to the hand");
            assertEquals(1, DrinkTree.widthHeldAt(trunk, taperFrom / TWO), DELTA, "the whole width early on");
            assertEquals(1, DrinkTree.widthHeldAt(trunk, taperFrom), DELTA, "and still where the taper starts");
            assertEquals(0.5, DrinkTree.widthHeldAt(trunk, midway), DELTA, "half of it midway down the taper");
            assertEquals(0.75, DrinkTree.widthHeldAt(trunk, taperFrom + (1 - taperFrom) / 4), DELTA, "straight");
            assertEquals(0, DrinkTree.widthHeldAt(trunk, 1), DELTA, "none at the glove");
            assertEquals(1 - Math.clamp((joinShare - taperFrom) / (1 - taperFrom), 0, 1),
                    DrinkTree.widthHeldAt(tributary, 1), DELTA,
                    "a tributary's end keeps the width its join's share of its own route leaves it");
            assertEquals(widthOf(full), DrinkTree.radiusAt(trunk, taperFrom), DELTA, "the fourth root to the taper");
            assertEquals((DrinkTree.THREAD + widthOf(mid)) / TWO, DrinkTree.radiusAt(trunk, midway), DELTA,
                    "halfway between the full width and the thread midway down the taper");
            assertEquals(DrinkTree.THREAD, DrinkTree.radiusAt(trunk, 1), DELTA, "a thread at the glove");
            assertEquals(DrinkStream.THINNEST, DrinkTree.THREAD, DELTA, "the thread is the thinnest the skin reads round");
            assertTrue(DrinkTree.THREAD < DrinkStream.WAIST / 4, "a thread is far thinner than one stream's waist");
        }

        @Test
        void theTrunkSwellsIntoAJoinSymmetricallyOverTheMergeLength() {
            assertEquals(0, DrinkTree.mergeRamp(-DrinkTree.MERGE), DELTA);
            assertEquals(0.5, DrinkTree.mergeRamp(0), DELTA);
            assertEquals(1, DrinkTree.mergeRamp(DrinkTree.MERGE), DELTA);
            assertEquals(1, DrinkTree.mergeRamp(DrinkTree.MERGE * 2), DELTA);
            assertEquals(0, DrinkTree.mergeRamp(-DrinkTree.MERGE * 2), DELTA);
        }

        @Test
        void aTrunkWhoseOwnLiquidHasPassedIsStillAsWideAsWhatFlowsThroughIt() {
            List<DrinkTree.Stream> streams = treeAt(START + tree().get(1).routeLength() / DrinkStream.FLOW);
            DrinkTree.Stream trunk = streams.getFirst();
            DrinkTree.Stream tributary = streams.get(1);
            double join = tributary.joinShare();
            DrinkTree.Flow joined = DrinkTree.flowOf(tributary, trunk, join);

            assertTrue(joined.radius() > 0, "the tributary's liquid has arrived at its join");
            assertEquals(0.5, joined.presence(), DELTA, "half swollen in at the join itself");
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
            double onTrunk = ownPath + DrinkTree.MERGE + DrinkStream.TIP + DrinkBody.FUNNEL;
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
