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
 * little toward the hand, a laid block keeps its route while its trunk flows
 * however the glove moves, a block whose trunk runs dry at its join re-roots
 * to the nearest flowing stream or the glove with its hand end gliding onto
 * the new course, and the nodes list each after its trunk
 * (decision unmake-waves-dissolve-by-crucible-cost).
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

            layout.place(List.of(FAR, NEAR), GLOVE_MOVED, NOW, DrinkLayout.EVERY);

            assertEquals(laid, List.copyOf(layout.nodes()));
            assertEquals(NEAR, layout.node(FAR).trunk());
            assertEquals(joinAt, layout.node(FAR).joinAt(), DELTA);
            assertFalse(layout.node(FAR).gliding());
        }

        @Test
        void aBlockAppearingLaterJoinsTheStandingLayoutOnlyWhereItStillFlows() {
            DrinkLayout layout = new DrinkLayout();
            layout.place(List.of(NEAR), GLOVE);
            layout.place(List.of(NEAR, LATER), GLOVE, NOW, DrinkLayout.EVERY);
            DrinkLayout dry = new DrinkLayout();
            dry.place(List.of(NEAR), GLOVE);
            dry.place(List.of(NEAR, LATER), GLOVE, NOW, NEAR_DRY);

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

            layout.place(List.of(FAR, NEAR), GLOVE, NOW, NEAR_DRY);
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
        void aBlockWhoseTrunkRunsDryReRootsToTheNearestFlowingStreamNearerTheHand() {
            DrinkLayout layout = new DrinkLayout();
            layout.place(List.of(FARTHER, FAR, NEAR), GLOVE);

            layout.place(List.of(FARTHER, FAR, NEAR), GLOVE, NOW, NEAR_DRY);

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

            layout.place(List.of(FAR, NEAR), GLOVE, NOW, (pos, distance) -> pos.equals(FAR));

            assertNull(layout.node(FAR).trunk());
            assertNull(layout.node(NEAR).trunk());
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
