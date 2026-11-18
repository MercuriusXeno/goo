package com.mercuriusxeno.goo.ability.yarn;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers what a led mob does each tick: fights a foe in reach; with none,
 * walks to its slot in the formation on the side its leader heads toward and
 * holds there once the leader stops, or walks to a leader it holds no spot
 * for from beyond the near distance and holds within it; and the formation's
 * slots, set by lead order, count and width.
 */
class LeadFollowTest {

    private static final Vec3 LEADER = new Vec3(10.5, 64.0, -3.5);
    private static final Vec3 BEYOND = LEADER.add(LeadFollow.NEAR_DISTANCE + 1.0, 0.0, 0.0);
    private static final Vec3 WITHIN = LEADER.add(LeadFollow.NEAR_DISTANCE - 1.0, 0.0, 0.0);
    private static final double WALK = 0.2;
    /** South is +z, so a leader facing north and backing up moves along +z. */
    private static final Vec3 BACKING_SOUTH = new Vec3(0.0, 0.0, WALK);
    private static final Vec3 STEPPING_WEST = new Vec3(-WALK, 0.0, 0.0);
    private static final Vec3 STANDING = Vec3.ZERO;
    private static final double CHICKEN = 0.4;
    private static final double COW = 0.9;
    private static final double HORSE = 1.3965;
    private static final double PLAYER = 0.6;
    /** A player at its largest scale, sixteen times a player's width. */
    private static final double GIANT_PLAYER = PLAYER * 16;
    private static final List<Double> ONE_COW = List.of(COW);
    private static final List<Double> FOUR_COWS = List.of(COW, COW, COW, COW);
    private static final LeadFollow.Slot LONE_COW_SLOT = LeadFollow.formation(PLAYER, ONE_COW).getFirst();
    /** A spot straight behind a leader that backed up south and stopped, where a lone cow's slot stands. */
    private static final Vec3 HELD_SPOT = LEADER.add(0.0, 0.0, LONE_COW_SLOT.distance());
    private static final double SAME = 1e-9;
    private static final boolean LOCKED_IN = true;
    private static final boolean UNLOCKED = false;

    private static Optional<Vec3> movingSpot(Vec3 velocity) {
        return LeadFollow.spot(Optional.empty(), LEADER, velocity, LONE_COW_SLOT);
    }

    private static List<Double> cows(int count) {
        return Collections.nCopies(count, COW);
    }

    @Nested
    class Choose {

        @Test
        void mobBeyondTheNearDistanceWalksToItsStandingLeader() {
            assertEquals(LeadFollow.Move.FOLLOW,
                    LeadFollow.choose(false, BEYOND, LEADER, Optional.empty(), UNLOCKED));
        }

        @Test
        void mobWithinTheNearDistanceOfAStandingLeaderHolds() {
            assertEquals(LeadFollow.Move.HOLD,
                    LeadFollow.choose(false, WITHIN, LEADER, Optional.empty(), UNLOCKED));
        }

        @Test
        void mobBesideARallierBelowTheStandingSpeedHolds() {
            Vec3 drift = new Vec3(LeadFollow.STANDING_SPEED / 2, 0.0, 0.0);
            Optional<Vec3> spot = LeadFollow.spot(Optional.empty(), LEADER, drift, LONE_COW_SLOT);
            assertEquals(LeadFollow.Move.HOLD, LeadFollow.choose(false, WITHIN, LEADER, spot, UNLOCKED));
        }

        @Test
        void mobWithAFoeInReachFightsWhereverItsLeaderStands() {
            assertEquals(LeadFollow.Move.FIGHT,
                    LeadFollow.choose(true, BEYOND, LEADER, Optional.empty(), UNLOCKED));
            assertEquals(LeadFollow.Move.FIGHT,
                    LeadFollow.choose(true, WITHIN, LEADER, Optional.empty(), UNLOCKED));
        }

        @Test
        void mobWithAFoeInReachFightsWhileItsLeaderMoves() {
            assertEquals(LeadFollow.Move.FIGHT,
                    LeadFollow.choose(true, WITHIN, LEADER, movingSpot(BACKING_SOUTH), UNLOCKED));
            assertEquals(LeadFollow.Move.FIGHT,
                    LeadFollow.choose(true, BEYOND, LEADER, movingSpot(STEPPING_WEST), UNLOCKED));
        }

        @Test
        void mobWithNoFoeTakesTheSideItsMovingLeaderHeadsToward() {
            assertEquals(LeadFollow.Move.CATCH_UP,
                    LeadFollow.choose(false, WITHIN, LEADER, movingSpot(BACKING_SOUTH), UNLOCKED));
            assertEquals(LeadFollow.Move.CATCH_UP,
                    LeadFollow.choose(false, BEYOND, LEADER, movingSpot(STEPPING_WEST), UNLOCKED));
        }

        @Test
        void mobShortOfItsHeldSpotKeepsWalkingThereOnceItsLeaderStops() {
            Optional<Vec3> spot = LeadFollow.spot(Optional.of(HELD_SPOT), LEADER, STANDING, LONE_COW_SLOT);
            assertEquals(LeadFollow.Move.CATCH_UP, LeadFollow.choose(false, LEADER, LEADER, spot, UNLOCKED));
        }

        @Test
        void mobAtItsHeldSpotMarchesThereAtItsStandingLeadersPace() {
            Optional<Vec3> spot = LeadFollow.spot(Optional.of(HELD_SPOT), LEADER, STANDING, LONE_COW_SLOT);
            Vec3 arrived = HELD_SPOT.add(LeadFollow.ARRIVAL_DISTANCE / 2, 0.0, 0.0);
            assertEquals(LeadFollow.Move.MARCH, LeadFollow.choose(false, arrived, LEADER, spot, UNLOCKED));
        }
    }

    /** each-rallied-mob-locks-into-a-fixed-slot */
    @Nested
    class LockIn {

        private static final Optional<Vec3> SLOT = Optional.of(HELD_SPOT);
        private static final Vec3 AT_THE_SLOT = HELD_SPOT.add(LeadFollow.ARRIVAL_DISTANCE / 2, 0.0, 0.0);
        private static final Vec3 BETWEEN = HELD_SPOT.add(
                (LeadFollow.ARRIVAL_DISTANCE + LeadFollow.RELEASE_DISTANCE) / 2, 0.0, 0.0);
        private static final Vec3 PAST_THE_RELEASE = HELD_SPOT.add(LeadFollow.RELEASE_DISTANCE + 0.5, 0.0, 0.0);

        @Test
        void anUnlockedMinionBeyondTheArrivalDistanceCatchesUp() {
            assertEquals(LeadFollow.Move.CATCH_UP, LeadFollow.choose(false, BETWEEN, LEADER, SLOT, UNLOCKED));
        }

        @Test
        void anUnlockedMinionWithinTheArrivalDistanceMarches() {
            assertEquals(LeadFollow.Move.MARCH, LeadFollow.choose(false, AT_THE_SLOT, LEADER, SLOT, UNLOCKED));
        }

        @Test
        void aLockedInMinionBetweenTheArrivalAndReleaseDistancesKeepsMarching() {
            assertEquals(LeadFollow.Move.MARCH, LeadFollow.choose(false, BETWEEN, LEADER, SLOT, LOCKED_IN));
        }

        @Test
        void aLockedInMinionBeyondTheReleaseDistanceCatchesUp() {
            assertEquals(LeadFollow.Move.CATCH_UP,
                    LeadFollow.choose(false, PAST_THE_RELEASE, LEADER, SLOT, LOCKED_IN));
        }

        @Test
        void aMinionWithAFoeInReachFightsLockedInOrNot() {
            for (Vec3 mob : List.of(AT_THE_SLOT, BETWEEN, PAST_THE_RELEASE)) {
                assertEquals(LeadFollow.Move.FIGHT, LeadFollow.choose(true, mob, LEADER, SLOT, UNLOCKED));
                assertEquals(LeadFollow.Move.FIGHT, LeadFollow.choose(true, mob, LEADER, SLOT, LOCKED_IN));
            }
        }
    }

    /** rallied-mobs-walk-around-their-rallier */
    @Nested
    class WalkPoint {

        private static final double RADIUS = LeadFollow.keepClearRadius(PLAYER, COW);
        private static final Vec3 WEST = LEADER.add(-3.0, 0.0, 0.0);
        private static final Vec3 EAST = LEADER.add(3.0, 0.0, 0.0);

        private double horizontalFromLeader(Vec3 point) {
            return point.subtract(LEADER).horizontalDistance();
        }

        /** The nearest the segment from one point to another passes the leader, horizontally. */
        private double nearestPass(Vec3 from, Vec3 to) {
            double dx = to.x - from.x;
            double dz = to.z - from.z;
            double length = dx * dx + dz * dz;
            double along = length == 0.0 ? 0.0
                    : Math.clamp(((LEADER.x - from.x) * dx + (LEADER.z - from.z) * dz) / length, 0.0, 1.0);
            return Math.hypot(from.x + along * dx - LEADER.x, from.z + along * dz - LEADER.z);
        }

        @Test
        void aClearLineWalksStraightToTheSlot() {
            Vec3 slot = LEADER.add(3.0, 0.0, 3.0);
            assertEquals(slot, LeadFollow.walkPoint(EAST, slot, LEADER, RADIUS));
        }

        @Test
        void aLeaderOnTheLineSendsTheMinionRoundItClear() {
            for (Vec3 slot : List.of(EAST, EAST.add(0.0, 0.0, 0.2), EAST.add(0.0, 0.0, -0.7))) {
                Vec3 point = LeadFollow.walkPoint(WEST, slot, LEADER, RADIUS);
                assertTrue(horizontalFromLeader(point) >= RADIUS, "the point " + point + " stands inside the radius");
                assertTrue(nearestPass(WEST, point) >= RADIUS,
                        "the walk to " + point + " passes " + nearestPass(WEST, point) + " from the leader");
            }
        }

        @Test
        void theWayRoundIsTheShorterOne() {
            Vec3 slot = EAST.add(0.0, 0.0, 0.5);
            Vec3 point = LeadFollow.walkPoint(WEST, slot, LEADER, RADIUS);
            assertTrue(!point.equals(slot) && point.z > LEADER.z,
                    "a slot south of the line should send the minion round the south side, not straight: " + point);
        }

        @Test
        void aMinionInsideTheRadiusStepsStraightOutOfIt() {
            Vec3 inside = LEADER.add(0.3, 0.0, 0.2);
            Vec3 point = LeadFollow.walkPoint(inside, EAST, LEADER, RADIUS);
            assertTrue(horizontalFromLeader(point) >= RADIUS, "the point " + point + " stands inside the radius");
            Vec3 out = inside.subtract(LEADER);
            Vec3 step = point.subtract(LEADER);
            assertEquals(0.0, out.x * step.z - out.z * step.x, SAME, "the point " + point + " is not straight out");
        }

        @Test
        void aMinionOnTopOfItsLeaderStepsOutOfTheRadius() {
            Vec3 point = LeadFollow.walkPoint(LEADER, EAST, LEADER, RADIUS);
            assertTrue(horizontalFromLeader(point) >= RADIUS, "the point " + point + " stands inside the radius");
        }

        @Test
        void theRadiusSpansBothHalfWidthsAndTheMargin() {
            assertEquals(PLAYER / 2 + COW / 2 + LeadFollow.KEEP_CLEAR_MARGIN, RADIUS, SAME);
        }

        @Test
        void everySlotStandsOutsideTheKeepClearRadius() {
            List<List<Double>> kinds = List.of(Collections.nCopies(12, CHICKEN), Collections.nCopies(12, HORSE),
                    List.of(CHICKEN, HORSE, COW, HORSE, CHICKEN, CHICKEN, HORSE, COW, COW, HORSE, CHICKEN, HORSE));
            for (double leader : List.of(PLAYER, GIANT_PLAYER)) {
                for (List<Double> kind : kinds) {
                    for (int count = 1; count <= kind.size(); count++) {
                        List<Double> widths = kind.subList(0, count);
                        List<LeadFollow.Slot> slots = LeadFollow.formation(leader, widths);
                        for (int index = 0; index < count; index++) {
                            double clear = LeadFollow.keepClearRadius(leader, widths.get(index));
                            assertTrue(slots.get(index).distance() >= clear, "slot " + index + " of " + count
                                    + " beside a leader " + leader + " wide stands inside " + clear);
                        }
                    }
                }
            }
        }
    }

    /** rallied-mobs-walk-around-their-rallier */
    @Nested
    class Aim {

        private static final double CATCH_UP_GROUND = 0.3;
        private static final Vec3 POINT = LEADER.add(2.0, 0.0, 2.0);
        private static final Vec3 MINION = LEADER.add(-2.0, 0.0, 1.0);

        private Vec3 headingOf(Vec3 aim) {
            return aim.subtract(MINION).normalize().scale(CATCH_UP_GROUND);
        }

        @Test
        void theMinionsWalkRelativeToItsMovingLeaderRunsStraightAtThePoint() {
            Vec3 relative = headingOf(LeadFollow.aim(MINION, POINT, STEPPING_WEST, CATCH_UP_GROUND))
                    .subtract(STEPPING_WEST);
            Vec3 toPoint = POINT.subtract(MINION);
            assertEquals(0.0, relative.x * toPoint.z - relative.z * toPoint.x, SAME, "the walk bends off the point");
            assertTrue(relative.dot(toPoint) > 0.0, "the walk runs away from the point");
        }

        @Test
        void aMinionOnThePointMovesAsItsLeaderMoves() {
            Vec3 aim = LeadFollow.aim(POINT, POINT, BACKING_SOUTH, WALK);
            Vec3 heading = aim.subtract(POINT).normalize();
            assertEquals(0.0, heading.x, SAME);
            assertEquals(1.0, heading.z, SAME);
        }
    }

    /** rallied-mobs-share-their-ralliers-speed */
    @Nested
    class Pace {

        /** A player's walk, in blocks a tick. */
        private static final double LEADER_WALK = 0.216;
        /** A led cow's movement speed: a cow's 0.2, lifted by the lead's half. */
        private static final double LED_COW_SPEED = 0.3;
        /** A led fox's movement speed: a fox's 0.3, lifted by the lead's half. */
        private static final double LED_FOX_SPEED = 0.45;
        private static final double MATCHED = 1e-9;

        private double groundSpeedAt(double ownSpeed, double leaderSpeed, boolean lockedIn) {
            return LeadFollow.groundSpeed(ownSpeed * LeadFollow.pace(leaderSpeed, ownSpeed, lockedIn));
        }

        @Test
        void aLockedInMinionSlowerThanItsLeaderWalksAtTheLeadersSpeed() {
            assertTrue(LeadFollow.groundSpeed(LED_COW_SPEED) < LEADER_WALK, "the cow should be the slower");
            assertEquals(LEADER_WALK, groundSpeedAt(LED_COW_SPEED, LEADER_WALK, LOCKED_IN), MATCHED);
        }

        @Test
        void aLockedInMinionFasterThanItsLeaderWalksAtTheLeadersSpeed() {
            assertTrue(LeadFollow.groundSpeed(LED_FOX_SPEED) > LEADER_WALK, "the fox should be the faster");
            assertEquals(LEADER_WALK, groundSpeedAt(LED_FOX_SPEED, LEADER_WALK, LOCKED_IN), MATCHED);
        }

        @Test
        void aMinionCatchingUpOutpacesItsLeader() {
            assertTrue(groundSpeedAt(LED_COW_SPEED, LEADER_WALK, UNLOCKED) > LEADER_WALK);
            assertTrue(groundSpeedAt(LED_FOX_SPEED, LEADER_WALK, UNLOCKED) > LEADER_WALK);
        }

        @Test
        void aMinionCatchingUpToAStandingLeaderStillMoves() {
            assertTrue(LeadFollow.pace(0.0, LED_COW_SPEED, UNLOCKED) > 0.0);
        }
    }

    /** slots-follow-rally-order-size-and-count */
    @Nested
    class Formation {

        @Test
        void theSameIndexCountAndWidthsAnswerTheSameSlot() {
            List<Double> widths = List.of(COW, HORSE, CHICKEN, COW, COW);
            for (int index = 0; index < widths.size(); index++) {
                assertEquals(LeadFollow.formation(PLAYER, widths).get(index),
                        LeadFollow.formation(PLAYER, new ArrayList<>(widths)).get(index), "slot " + index + " moved");
            }
        }

        @Test
        void aFormationAnswersOneSlotPerMinion() {
            assertEquals(12, LeadFollow.formation(PLAYER, cows(12)).size());
        }

        @Test
        void smallMobsStandAtLeastTheirHalfWidthsApart() {
            assertEveryPairApart(Collections.nCopies(12, CHICKEN));
        }

        @Test
        void narrowAndWideMobsStandAtLeastTheirHalfWidthsApart() {
            assertEveryPairApart(List.of(CHICKEN, HORSE, CHICKEN, HORSE, COW, CHICKEN, HORSE, HORSE, CHICKEN, COW));
        }

        @Test
        void theLastSlotOfALargerFormationStandsFartherOut() {
            double small = LeadFollow.formation(PLAYER, cows(3)).getLast().distance();
            double large = LeadFollow.formation(PLAYER, cows(12)).getLast().distance();
            assertTrue(large > small, "twelve cows' last slot stands at " + large + ", three cows' at " + small);
        }

        @Test
        void aSlotBesideAWiderMobStandsFartherOut() {
            double narrow = LeadFollow.formation(PLAYER, List.of(COW, COW, COW)).getFirst().distance();
            double wide = LeadFollow.formation(PLAYER, List.of(COW, HORSE, COW)).getFirst().distance();
            assertTrue(wide > narrow, "beside a horse the slot stands at " + wide + ", beside a cow at " + narrow);
        }

        private void assertEveryPairApart(List<Double> widths) {
            List<LeadFollow.Slot> slots = LeadFollow.formation(PLAYER, widths);
            for (int one = 0; one < slots.size(); one++) {
                for (int other = one + 1; other < slots.size(); other++) {
                    double apart = Math.hypot(slots.get(one).ahead() - slots.get(other).ahead(),
                            slots.get(one).aside() - slots.get(other).aside());
                    double needed = (widths.get(one) + widths.get(other)) / 2;
                    assertTrue(apart >= needed,
                            "slots " + one + " and " + other + " stand " + apart + " apart, need " + needed);
                }
            }
        }
    }

    /** slots-follow-rally-order-size-and-count */
    @Nested
    class LeadOrder {

        private static final UUID LOW = new UUID(0L, 1L);
        private static final UUID MIDDLE = new UUID(0L, 2L);
        private static final UUID HIGH = new UUID(0L, 3L);

        private record Minion(long leadOrder, UUID uuid) {
        }

        private List<Minion> ordered(List<Minion> minions) {
            return LeadFollow.inLeadOrder(minions, Minion::leadOrder, Minion::uuid);
        }

        @Test
        void theEarliestLeadTakesIndexZeroAndLaterOnesRise() {
            Minion first = new Minion(100L, HIGH);
            Minion second = new Minion(200L, LOW);
            Minion third = new Minion(300L, MIDDLE);
            List<Minion> ordered = ordered(List.of(third, first, second));
            assertEquals(0, ordered.indexOf(first));
            assertEquals(1, ordered.indexOf(second));
            assertEquals(2, ordered.indexOf(third));
        }

        @Test
        void anEqualStampBreaksByUuidOnEveryCall() {
            Minion low = new Minion(100L, LOW);
            Minion high = new Minion(100L, HIGH);
            assertEquals(List.of(low, high), ordered(List.of(high, low)));
            assertEquals(List.of(low, high), ordered(List.of(low, high)));
        }

        @Test
        void aMinionLeavingMovesEveryLaterMinionUpOne() {
            Minion first = new Minion(100L, LOW);
            Minion second = new Minion(200L, MIDDLE);
            Minion third = new Minion(300L, HIGH);
            List<Minion> all = ordered(List.of(first, second, third));
            List<Minion> afterTheFirstLeaves = ordered(List.of(third, second));
            assertEquals(all.indexOf(second) - 1, afterTheFirstLeaves.indexOf(second));
            assertEquals(all.indexOf(third) - 1, afterTheFirstLeaves.indexOf(third));
        }
    }

    /** each-rallied-mob-locks-into-a-fixed-slot */
    @Nested
    class Destination {

        @Test
        void leaderFacingNorthAndBackingSouthPutsEveryMinionSouthOfIt() {
            // the destination reads travel alone, so a leader facing north while it backs up heads south
            for (LeadFollow.Slot slot : LeadFollow.formation(PLAYER, FOUR_COWS)) {
                Vec3 spot = LeadFollow.destination(LEADER, BACKING_SOUTH, slot).orElseThrow();
                assertTrue(spot.z > LEADER.z + 1.0, slot + " lands at " + spot);
            }
        }

        @Test
        void leaderSteppingWestPutsEveryMinionWestOfIt() {
            for (LeadFollow.Slot slot : LeadFollow.formation(PLAYER, FOUR_COWS)) {
                Vec3 spot = LeadFollow.destination(LEADER, STEPPING_WEST, slot).orElseThrow();
                assertTrue(spot.x < LEADER.x - 1.0, slot + " lands at " + spot);
            }
        }

        @Test
        void aMinionHoldsItsSlotRelativeToTravelWhenTheLeaderTurns() {
            for (LeadFollow.Slot slot : LeadFollow.formation(PLAYER, FOUR_COWS)) {
                Vec3 south = LeadFollow.destination(LEADER, BACKING_SOUTH, slot).orElseThrow().subtract(LEADER);
                Vec3 west = LeadFollow.destination(LEADER, STEPPING_WEST, slot).orElseThrow().subtract(LEADER);
                // the quarter turn taking south travel (0, 1) to west travel (-1, 0) takes (x, z) to (-z, x)
                assertEquals(-south.z, west.x, SAME, slot + " moved off its place under westward travel");
                assertEquals(south.x, west.z, SAME, slot + " moved off its place under westward travel");
            }
        }

        @Test
        void neighbouringSlotsStandOnEitherSideOfTheTravelLine() {
            List<LeadFollow.Slot> slots = LeadFollow.formation(PLAYER, List.of(COW, COW));
            Vec3 first = LeadFollow.destination(LEADER, BACKING_SOUTH, slots.get(0)).orElseThrow();
            Vec3 second = LeadFollow.destination(LEADER, BACKING_SOUTH, slots.get(1)).orElseThrow();
            assertTrue((first.x - LEADER.x) * (second.x - LEADER.x) < 0.0,
                    "two cows' slots " + first + " and " + second + " stand on one side of the travel line");
        }

        @Test
        void leaderBelowTheStandingSpeedGivesNoOffsetDestination() {
            Vec3 drift = new Vec3(0.0, 0.0, LeadFollow.STANDING_SPEED / 2);
            assertEquals(Optional.empty(), LeadFollow.destination(LEADER, drift, LONE_COW_SLOT));
        }

        @Test
        void leaderFallingStraightDownGivesNoOffsetDestination() {
            assertEquals(Optional.empty(), LeadFollow.destination(LEADER, new Vec3(0.0, -1.0, 0.0), LONE_COW_SLOT));
        }
    }

    @Nested
    class Spot {

        @Test
        void standingLeaderKeepsTheHeldSpotNearIt() {
            assertEquals(Optional.of(HELD_SPOT),
                    LeadFollow.spot(Optional.of(HELD_SPOT), LEADER, STANDING, LONE_COW_SLOT));
        }

        @Test
        void standingLeaderKeepsAnOuterSlotsHeldSpot() {
            LeadFollow.Slot outer = LeadFollow.formation(PLAYER, cows(12)).getLast();
            Vec3 held = LeadFollow.destination(LEADER, BACKING_SOUTH, outer).orElseThrow();
            assertEquals(Optional.of(held), LeadFollow.spot(Optional.of(held), LEADER, STANDING, outer));
        }

        @Test
        void movingLeaderReplacesTheHeldSpotWithAFreshOne() {
            Optional<Vec3> spot = LeadFollow.spot(Optional.of(HELD_SPOT), LEADER, STEPPING_WEST, LONE_COW_SLOT);
            assertTrue(spot.orElseThrow().x < LEADER.x - 1.0, "the spot stayed behind at " + spot);
        }

        @Test
        void leaderFarFromItsHeldSpotDropsIt() {
            Vec3 teleported = LEADER.add(50.0, 0.0, 0.0);
            assertEquals(Optional.empty(),
                    LeadFollow.spot(Optional.of(HELD_SPOT), teleported, STANDING, LONE_COW_SLOT));
        }
    }

    @Nested
    class SmoothVelocity {

        @Test
        void aSingleStutterKeepsTheHeading() {
            Vec3 heading = new Vec3(0.0, 0.0, WALK);
            Vec3 smoothed = LeadFollow.smoothVelocity(heading, new Vec3(0.0, 0.0, -WALK));
            assertTrue(smoothed.z > LeadFollow.STANDING_SPEED, "the stutter swung the heading to " + smoothed);
        }

        @Test
        void steadyStepsSettleOnTheirVelocity() {
            Vec3 smoothed = Vec3.ZERO;
            for (int tick = 0; tick < 40; tick++) {
                smoothed = LeadFollow.smoothVelocity(smoothed, STEPPING_WEST);
            }
            assertEquals(-WALK, smoothed.x, 1e-3);
        }

        @Test
        void aTeleportStepCountsAsStanding() {
            Vec3 smoothed = LeadFollow.smoothVelocity(BACKING_SOUTH, new Vec3(100.0, 0.0, 0.0));
            assertEquals(Vec3.ZERO, smoothed);
        }
    }
}
