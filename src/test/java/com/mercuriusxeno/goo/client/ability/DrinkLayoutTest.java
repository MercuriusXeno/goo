package com.mercuriusxeno.goo.client.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A drink's layout paths every stream to the hand: the first block runs to
 * the glove, a later block joins the nearest flowing stream nearer the hand a
 * little toward the hand from its nearest point, a laid block keeps its route
 * while its trunk flows however the glove moves, a block whose trunk runs dry
 * at its join re-roots by the same search or to the glove with its hand end
 * gliding onto the new course, and the nodes list each after its trunk
 * (decision unmake-waves-dissolve-by-crucible-cost); with an aim, the block
 * the aim line passes through nearest the hand is the spine, and side blocks
 * meld with their neighbours on the way to it, an inverse tree whose every
 * chain ends at the spine (decisions unmake-streams-collapse-into-a-central-trunk
 * and unmake-streams-meld-into-an-inverse-tree).
 */
class DrinkLayoutTest {

    private static final double DELTA = 1e-9;
    private static final Vec3 GLOVE = new Vec3(1.5, 2.2, 3.1);
    private static final Vec3 GLOVE_MOVED = new Vec3(1.5, 3.2, 1.1);
    private static final BlockPos NEAR = new BlockPos(7, 2, 3);
    private static final BlockPos FAR = new BlockPos(7, 3, 4);
    private static final BlockPos FARTHER = new BlockPos(7, 4, 5);
    private static final BlockPos LATER = new BlockPos(7, 1, 2);
    private static final double NOW = 10;
    private static final double SETTLED = 1e-6;
    private static final double TWENTY = 20;
    private static final double HALF_BLOCK = 0.5;
    private static final double THIRTY_ONE_DEGREES = Math.toDegrees(Math.atan(0.6));

    /** Every stream flowing but the near block's, run dry along all of it. */
    private static final DrinkLayout.Flowing NEAR_DRY = (pos, distance) -> !pos.equals(NEAR);

    @Nested
    class Laying {

        @Test
        void theFirstBlockRunsToTheGloveFromHalfASpanBehindItsMiddle() {
            DrinkLayout layout = new DrinkLayout();
            layout.place(List.of(NEAR), GLOVE);

            DrinkLayout.Node node = layout.node(NEAR);
            Vec3 center = Vec3.atCenterOf(NEAR);
            assertNull(node.trunk());
            assertEquals(DrinkStream.BLOCK_SPAN / 2, node.farSide().distanceTo(center), DELTA);
            assertTrue(center.subtract(node.farSide()).dot(GLOVE.subtract(center)) > 0);
            assertEquals(0, layout.lineOf(node, GLOVE).to().distanceTo(GLOVE), DELTA);
            assertEquals(GLOVE, node.endToward(GLOVE, NOW));
        }

        @Test
        void aFartherBlockJoinsTheNearerStreamALittleTowardTheHand() {
            DrinkLayout layout = new DrinkLayout();
            layout.place(List.of(FAR, NEAR), GLOVE);

            DrinkLayout.Node trunk = layout.node(NEAR);
            DrinkLayout.Node tributary = layout.node(FAR);
            DrinkLayout.Line line = layout.lineOf(trunk, GLOVE);

            assertEquals(List.of(trunk, tributary), List.copyOf(layout.nodes()));
            assertEquals(NEAR, tributary.trunk());
            assertNull(trunk.trunk(), "the nearer block never joins the farther");
            assertEquals(line.nearestDistance(Vec3.atCenterOf(FAR)) + DrinkLayout.LEAD, tributary.joinAt(), DELTA);
            assertEquals(0, layout.lineOf(tributary, GLOVE).to().distanceTo(line.pointAt(tributary.joinAt())), DELTA);
        }

        @Test
        void aLaidBlockKeepsItsRouteWhileItsTrunkFlowsHoweverTheGloveMoves() {
            DrinkLayout layout = new DrinkLayout();
            layout.place(List.of(FAR, NEAR), GLOVE);
            List<DrinkLayout.Node> laid = List.copyOf(layout.nodes());
            double joinAt = layout.node(FAR).joinAt();

            layout.place(List.of(FAR, NEAR), GLOVE_MOVED, null, NOW,DrinkLayout.EVERY);

            assertEquals(laid, List.copyOf(layout.nodes()));
            assertEquals(NEAR, layout.node(FAR).trunk());
            assertEquals(joinAt, layout.node(FAR).joinAt(), DELTA);
            assertFalse(layout.node(FAR).gliding());
        }

        @Test
        void aBlockAppearingLaterJoinsTheStandingLayoutOnlyWhereItStillFlows() {
            DrinkLayout layout = new DrinkLayout();
            layout.place(List.of(NEAR), GLOVE);
            layout.place(List.of(NEAR, LATER), GLOVE, null, NOW,DrinkLayout.EVERY);
            DrinkLayout dry = new DrinkLayout();
            dry.place(List.of(NEAR), GLOVE);
            dry.place(List.of(NEAR, LATER), GLOVE, null, NOW,NEAR_DRY);

            assertEquals(NEAR, layout.node(LATER).trunk());
            assertEquals(2, layout.nodes().size());
            assertTrue(layout.node(LATER).joinAt() > 0);
            assertNull(dry.node(LATER).trunk(), "a dry stream takes no new block");
        }
    }

    @Nested
    class Rerooting {

        @Test
        void aBlockWhoseTrunkRunsDryReRootsToTheGloveItsHandEndGlidingOntoTheNewCourse() {
            DrinkLayout layout = new DrinkLayout();
            layout.place(List.of(FAR, NEAR), GLOVE);
            Vec3 oldEnd = layout.lineOf(layout.node(FAR), GLOVE).to();

            layout.place(List.of(FAR, NEAR), GLOVE, null, NOW,NEAR_DRY);
            DrinkLayout.Node rerooted = layout.node(FAR);

            assertNull(rerooted.trunk());
            assertTrue(rerooted.gliding());
            assertEquals(0, rerooted.endToward(GLOVE, NOW).distanceTo(oldEnd), DELTA, "the end starts where it was");
            Vec3 soon = rerooted.endToward(GLOVE, NOW + 1);
            assertTrue(soon.distanceTo(oldEnd) > 0 && soon.distanceTo(GLOVE) < oldEnd.distanceTo(GLOVE),
                    "a tick on the end has set off for the glove");
            assertEquals(0, rerooted.endToward(GLOVE, NOW + TWENTY * DrinkLayout.GLIDE).distanceTo(GLOVE), SETTLED);
            assertFalse(rerooted.gliding(), "the glide is over once the end is on its course");
        }

        @Test
        void aBlockWhoseTrunkRunsDryReRootsOntoTheNextSpine() {
            DrinkLayout layout = new DrinkLayout();
            layout.place(List.of(FARTHER, FAR, NEAR), GLOVE);

            layout.place(List.of(FARTHER, FAR, NEAR), GLOVE, null, NOW,NEAR_DRY);

            assertNull(layout.node(FAR).trunk());
            assertEquals(FAR, layout.node(FARTHER).trunk());
            assertTrue(layout.node(FARTHER).joinAt() > 0);
            List<DrinkLayout.Node> ordered = List.copyOf(layout.nodes());
            assertTrue(ordered.indexOf(layout.node(FAR)) < ordered.indexOf(layout.node(FARTHER)),
                    "a trunk is listed before the stream joining it");
        }

        @Test
        void aStreamNeverReRootsOntoOneThatRunsDownIt() {
            DrinkLayout layout = new DrinkLayout();
            layout.place(List.of(FAR, NEAR), GLOVE);

            layout.place(List.of(FAR, NEAR), GLOVE, null, NOW,(pos, distance) -> pos.equals(FAR));

            assertNull(layout.node(FAR).trunk());
            assertNull(layout.node(NEAR).trunk());
        }
    }

    /**
     * A cone aimed east down y 1.6, z 0.5: the line passes through ON_LINE
     * and NEXT_ON_LINE, while OFF_LINE_NEAREST sits nearer the glove than
     * either.
     * decision unmake-streams-collapse-into-a-central-trunk
     */
    @Nested
    class CentralTrunk {

        private static final Vec3 HAND = new Vec3(0.5, 1.2, 0.3);
        private static final DrinkLayout.Aim AIM = new DrinkLayout.Aim(new Vec3(0, 1.6, 0.5), new Vec3(1, 0, 0));
        private static final BlockPos ON_LINE = new BlockPos(3, 1, 0);
        private static final BlockPos NEXT_ON_LINE = new BlockPos(5, 1, 0);
        private static final BlockPos OFF_LINE_NEAREST = new BlockPos(2, 0, 1);
        private static final List<BlockPos> CONE = List.of(ON_LINE, NEXT_ON_LINE, OFF_LINE_NEAREST,
                new BlockPos(4, 2, 1), new BlockPos(5, 0, -1), new BlockPos(6, 2, -1), new BlockPos(4, 0, 0));
        private static final DrinkLayout.Flowing SPINE_DRY = (pos, distance) -> !pos.equals(ON_LINE);
        /** The spine, and two neighbouring blocks three and four blocks off the aim line. */
        private static final List<BlockPos> SIDE_BY_SIDE = List.of(ON_LINE, new BlockPos(3, 1, 3),
                new BlockPos(3, 1, 4));
        /** Side blocks two rows off the aim line, each a block from its neighbours and three from the line. */
        private static final List<BlockPos> WIDE_CONE = List.of(ON_LINE, NEXT_ON_LINE, new BlockPos(4, 1, 3),
                new BlockPos(5, 1, 3), new BlockPos(6, 1, 3), new BlockPos(4, 1, 4), new BlockPos(5, 1, 4),
                new BlockPos(6, 1, 4));

        private DrinkLayout laid() {
            DrinkLayout layout = new DrinkLayout();
            layout.place(CONE, HAND, AIM, 0, DrinkLayout.EVERY);
            return layout;
        }

        private List<BlockPos> feedersOf(DrinkLayout layout, BlockPos trunk) {
            return layout.nodes().stream().filter(node -> trunk.equals(node.trunk())).map(DrinkLayout.Node::pos)
                    .toList();
        }

        @Test
        void theBlockTheAimLinePassesThroughNearestTheHandIsTheSpineEveryChainEndsAtIt() {
            DrinkLayout layout = laid();

            assertTrue(Vec3.atCenterOf(OFF_LINE_NEAREST).distanceTo(HAND) < Vec3.atCenterOf(ON_LINE).distanceTo(HAND),
                    "the spine is not merely the block nearest the glove");
            assertEquals(ON_LINE, layout.spine());
            assertNull(layout.node(ON_LINE).trunk());
            for (BlockPos pos : CONE) {
                if (!pos.equals(ON_LINE)) {
                    assertTrue(layout.node(pos).trunk() != null, pos + " feeds a trunk rather than the glove");
                    assertEquals(ON_LINE, rootOf(layout, pos), pos + "'s chain ends at the spine");
                }
            }
        }

        /**
         * Two neighbouring blocks off the aim line meld as they leave: the
         * second's way to the spine first runs near the first's stream at its
         * own block, so it joins there, a lead past that stream's nearest
         * point, rather than running on to the spine; the first, whose way
         * meets no other stream, joins the spine itself.
         * decision unmake-streams-meld-into-an-inverse-tree
         */
        @Test
        void twoNeighbouringBlocksMeldAsTheyLeaveAndALoneBlockJoinsTheSpine() {
            DrinkLayout layout = new DrinkLayout();
            layout.place(SIDE_BY_SIDE, HAND, AIM, 0, DrinkLayout.EVERY);
            DrinkLayout.Node left = layout.node(SIDE_BY_SIDE.get(1));
            DrinkLayout.Node right = layout.node(SIDE_BY_SIDE.get(2));

            DrinkLayout.Node first = ON_LINE.equals(left.trunk()) ? left : right;
            DrinkLayout.Node second = first == left ? right : left;
            assertEquals(ON_LINE, first.trunk(), "the block laid first meets no stream on its way and joins the spine");
            assertEquals(first.pos(), second.trunk(), "the second melds into the first's stream");
            DrinkLayout.Line joined = layout.lineOf(first, HAND);
            double nearest = joined.nearestDistance(Vec3.atCenterOf(second.pos()));
            assertTrue(joined.pointAt(nearest).distanceTo(Vec3.atCenterOf(second.pos())) < DrinkLayout.MELD_REACH,
                    "they run near each other as they leave their blocks");
            assertEquals(Math.min(joined.length(), nearest + DrinkLayout.LEAD), second.joinAt(), DELTA,
                    "joining a lead past the first stream's point nearest where they first run near");
            assertEquals(ON_LINE, rootOf(layout, second.pos()));
        }

        private BlockPos rootOf(DrinkLayout layout, BlockPos pos) {
            BlockPos root = pos;
            for (int hops = 0; layout.node(root).trunk() != null && hops <= WIDE_CONE.size() + CONE.size(); hops++) {
                root = layout.node(root).trunk();
            }
            return root;
        }

        @Test
        void aDrySpineHandsOffToTheNextAimLineBlockEveryFeederGlidingOntoAFlowingStream() {
            DrinkLayout layout = laid();
            List<BlockPos> feeders = feedersOf(layout, ON_LINE);

            layout.place(CONE, HAND, AIM, NOW, SPINE_DRY);

            assertFalse(feeders.isEmpty(), "the spine had feeders");
            assertEquals(NEXT_ON_LINE, layout.spine());
            assertNull(layout.node(NEXT_ON_LINE).trunk());
            for (BlockPos pos : feeders) {
                DrinkLayout.Node node = layout.node(pos);
                assertTrue(node.gliding(), pos + " glides onto its new course");
                if (!pos.equals(NEXT_ON_LINE)) {
                    assertTrue(node.trunk() != null && SPINE_DRY.at(node.trunk(), node.joinAt()),
                            pos + " re-roots onto a flowing stream");
                    assertEquals(NEXT_ON_LINE, rootOf(layout, pos), pos + "'s chain ends at the new spine");
                }
            }
        }

        @Test
        void aSpineStillCarryingAFeedersGooAtItsJoinKeepsThatFeeder() {
            DrinkLayout layout = laid();
            BlockPos feeder = feedersOf(layout, ON_LINE).getFirst();
            double joinAt = layout.node(feeder).joinAt();
            DrinkLayout.Flowing ridden = (pos, distance) -> !pos.equals(ON_LINE) || Math.abs(distance - joinAt) < DELTA;

            layout.place(CONE, HAND, AIM, NOW, ridden);

            assertEquals(ON_LINE, layout.spine());
            assertEquals(ON_LINE, layout.node(feeder).trunk());
            assertEquals(joinAt, layout.node(feeder).joinAt(), DELTA);
            assertFalse(layout.node(feeder).gliding());
        }
    }

    @Test
    void theTrunkArrivesDownALineLiftedOverTheLookInOverTheFingertips() {
        Vec3 east = new Vec3(1, 0, 0);

        Vec3 arrival = DrinkLayout.arrivalOf(east);

        assertEquals(1, arrival.length(), DELTA, "a unit direction");
        assertTrue(arrival.y > 0, "lifted above the look");
        assertEquals(THIRTY_ONE_DEGREES, Math.toDegrees(Math.atan2(arrival.y, arrival.x)), 0.1,
                "about thirty degrees over the look, so it comes in from ahead and above");
        assertEquals(0, arrival.z, DELTA, "and straight ahead otherwise");
    }

    @Test
    void thePullFollowsTheLookWithALagAndSettlesAgainstIt() {
        DrinkLayout layout = new DrinkLayout();
        Vec3 east = new Vec3(1, 0, 0);
        Vec3 north = new Vec3(0, 0, -1);
        Vec3 fromEast = DrinkLayout.arrivalOf(east).reverse();
        Vec3 fromNorth = DrinkLayout.arrivalOf(north).reverse();

        Vec3 first = layout.pullToward(east, 0);
        Vec3 soon = layout.pullToward(north, 1);
        Vec3 settled = layout.pullToward(north, 1 + TWENTY * DrinkLayout.PULL_LAG);

        assertEquals(0, first.distanceTo(fromEast), DELTA, "the pull flows back down the arrival line");
        assertTrue(soon.dot(fromEast) > 0.5, "a tick later the pull still mostly faces the old look");
        assertTrue(soon.distanceTo(fromNorth) < fromEast.distanceTo(fromNorth), "and has begun to turn");
        assertEquals(0, settled.distanceTo(fromNorth), 1e-6);
        assertEquals(1, soon.length(), 1e-9);
    }
}
