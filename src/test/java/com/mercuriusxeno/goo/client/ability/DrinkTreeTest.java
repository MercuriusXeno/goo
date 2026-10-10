package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.program.SiphonRule;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.List;
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
        void theTrunkThinsToAThreadOverItsLastBlockIntoTheGlove() {
            List<DrinkTree.Stream> streams = treeAt(START + tree().get(1).routeLength() / DrinkStream.FLOW);
            DrinkTree.Stream trunk = streams.getFirst();
            DrinkTree.Stream tributary = streams.get(1);
            double length = trunk.path().length();
            double aBlockOut = 1 - DrinkTree.THIN_INTO_HAND / length;
            double halfOut = 1 - DrinkTree.THIN_INTO_HAND / TWO / length;
            DrinkTree.Flow out = DrinkTree.flowOf(trunk, trunk, aBlockOut).plus(DrinkTree.flowOf(tributary, trunk,
                    aBlockOut));
            DrinkTree.Flow half = DrinkTree.flowOf(trunk, trunk, halfOut).plus(DrinkTree.flowOf(tributary, trunk,
                    halfOut));
            DrinkTree.Flow atGlove = DrinkTree.flowOf(trunk, trunk, 1).plus(DrinkTree.flowOf(tributary, trunk, 1));

            assertEquals(1, DrinkTree.widthHeldAt(trunk, aBlockOut), DELTA, "the whole width a block out");
            assertEquals(0.5, DrinkTree.widthHeldAt(trunk, halfOut), DELTA);
            assertEquals(0, DrinkTree.widthHeldAt(trunk, 1), DELTA, "none at the glove");
            assertEquals(1, DrinkTree.widthHeldAt(tributary, 1), DELTA, "a tributary keeps its width to its join");
            assertEquals(TWO, out.presence(), DELTA, "both streams wholly there");
            assertEquals(TWO, atGlove.presence(), DELTA);
            assertEquals(widthOf(out), DrinkTree.radiusAt(trunk, aBlockOut), DELTA, "the fourth root a block out");
            assertEquals((DrinkTree.THREAD + widthOf(half)) / TWO, DrinkTree.radiusAt(trunk, halfOut), DELTA,
                    "halfway between the full width and the thread half a block out");
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
        void theTravelTimeReadsCleanlyAtAndJustShortOfThePathsEnd() {
            DrinkTree.Stream stream = loneAt(RAMPED);
            double length = stream.path().length();
            double shy = Math.nextDown(length);

            assertEquals(stream.timeTo(length), stream.timeTo(shy), 1e-9);
            assertEquals(length / DrinkStream.FLOW, stream.timeTo(shy), 1e-6);
        }
    }
}
