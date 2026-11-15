package com.mercuriusxeno.goo.ability.zoo;

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
 * Covers what a rallied mob does each tick: fights a foe in reach; with none,
 * walks to its slot in the formation on the side its rallier heads toward and
 * holds there once the rallier stops, or walks to a rallier it holds no spot
 * for from beyond the near distance and holds within it; and the formation's
 * slots, set by rally order, count and width.
 */
class RallyFollowTest {

    private static final Vec3 RALLIER = new Vec3(10.5, 64.0, -3.5);
    private static final Vec3 BEYOND = RALLIER.add(RallyFollow.NEAR_DISTANCE + 1.0, 0.0, 0.0);
    private static final Vec3 WITHIN = RALLIER.add(RallyFollow.NEAR_DISTANCE - 1.0, 0.0, 0.0);
    private static final double WALK = 0.2;
    /** South is +z, so a rallier facing north and backing up moves along +z. */
    private static final Vec3 BACKING_SOUTH = new Vec3(0.0, 0.0, WALK);
    private static final Vec3 STEPPING_WEST = new Vec3(-WALK, 0.0, 0.0);
    private static final Vec3 STANDING = Vec3.ZERO;
    private static final double CHICKEN = 0.4;
    private static final double COW = 0.9;
    private static final double HORSE = 1.3965;
    private static final List<Double> ONE_COW = List.of(COW);
    private static final List<Double> FOUR_COWS = List.of(COW, COW, COW, COW);
    private static final RallyFollow.Slot LONE_COW_SLOT = RallyFollow.formation(ONE_COW).getFirst();
    /** A spot straight behind a rallier that backed up south and stopped, where a lone cow's slot stands. */
    private static final Vec3 HELD_SPOT = RALLIER.add(0.0, 0.0, LONE_COW_SLOT.distance());
    private static final double SAME = 1e-9;
    private static final boolean LOCKED_IN = true;
    private static final boolean UNLOCKED = false;

    private static Optional<Vec3> movingSpot(Vec3 velocity) {
        return RallyFollow.spot(Optional.empty(), RALLIER, velocity, LONE_COW_SLOT);
    }

    private static List<Double> cows(int count) {
        return Collections.nCopies(count, COW);
    }

    @Nested
    class Choose {

        @Test
        void mobBeyondTheNearDistanceWalksToItsStandingRallier() {
            assertEquals(RallyFollow.Move.FOLLOW,
                    RallyFollow.choose(false, BEYOND, RALLIER, Optional.empty(), UNLOCKED));
        }

        @Test
        void mobWithinTheNearDistanceOfAStandingRallierHolds() {
            assertEquals(RallyFollow.Move.HOLD,
                    RallyFollow.choose(false, WITHIN, RALLIER, Optional.empty(), UNLOCKED));
        }

        @Test
        void mobBesideARallierBelowTheStandingSpeedHolds() {
            Vec3 drift = new Vec3(RallyFollow.STANDING_SPEED / 2, 0.0, 0.0);
            Optional<Vec3> spot = RallyFollow.spot(Optional.empty(), RALLIER, drift, LONE_COW_SLOT);
            assertEquals(RallyFollow.Move.HOLD, RallyFollow.choose(false, WITHIN, RALLIER, spot, UNLOCKED));
        }

        @Test
        void mobWithAFoeInReachFightsWhereverItsRallierStands() {
            assertEquals(RallyFollow.Move.FIGHT,
                    RallyFollow.choose(true, BEYOND, RALLIER, Optional.empty(), UNLOCKED));
            assertEquals(RallyFollow.Move.FIGHT,
                    RallyFollow.choose(true, WITHIN, RALLIER, Optional.empty(), UNLOCKED));
        }

        @Test
        void mobWithAFoeInReachFightsWhileItsRallierMoves() {
            assertEquals(RallyFollow.Move.FIGHT,
                    RallyFollow.choose(true, WITHIN, RALLIER, movingSpot(BACKING_SOUTH), UNLOCKED));
            assertEquals(RallyFollow.Move.FIGHT,
                    RallyFollow.choose(true, BEYOND, RALLIER, movingSpot(STEPPING_WEST), UNLOCKED));
        }

        @Test
        void mobWithNoFoeTakesTheSideItsMovingRallierHeadsToward() {
            assertEquals(RallyFollow.Move.CATCH_UP,
                    RallyFollow.choose(false, WITHIN, RALLIER, movingSpot(BACKING_SOUTH), UNLOCKED));
            assertEquals(RallyFollow.Move.CATCH_UP,
                    RallyFollow.choose(false, BEYOND, RALLIER, movingSpot(STEPPING_WEST), UNLOCKED));
        }

        @Test
        void mobShortOfItsHeldSpotKeepsWalkingThereOnceItsRallierStops() {
            Optional<Vec3> spot = RallyFollow.spot(Optional.of(HELD_SPOT), RALLIER, STANDING, LONE_COW_SLOT);
            assertEquals(RallyFollow.Move.CATCH_UP, RallyFollow.choose(false, RALLIER, RALLIER, spot, UNLOCKED));
        }

        @Test
        void mobAtItsHeldSpotMarchesThereAtItsStandingRalliersPace() {
            Optional<Vec3> spot = RallyFollow.spot(Optional.of(HELD_SPOT), RALLIER, STANDING, LONE_COW_SLOT);
            Vec3 arrived = HELD_SPOT.add(RallyFollow.ARRIVAL_DISTANCE / 2, 0.0, 0.0);
            assertEquals(RallyFollow.Move.MARCH, RallyFollow.choose(false, arrived, RALLIER, spot, UNLOCKED));
        }
    }

    /** each-rallied-mob-locks-into-a-fixed-slot */
    @Nested
    class LockIn {

        private static final Optional<Vec3> SLOT = Optional.of(HELD_SPOT);
        private static final Vec3 AT_THE_SLOT = HELD_SPOT.add(RallyFollow.ARRIVAL_DISTANCE / 2, 0.0, 0.0);
        private static final Vec3 BETWEEN = HELD_SPOT.add(
                (RallyFollow.ARRIVAL_DISTANCE + RallyFollow.RELEASE_DISTANCE) / 2, 0.0, 0.0);
        private static final Vec3 PAST_THE_RELEASE = HELD_SPOT.add(RallyFollow.RELEASE_DISTANCE + 0.5, 0.0, 0.0);

        @Test
        void anUnlockedMinionBeyondTheArrivalDistanceCatchesUp() {
            assertEquals(RallyFollow.Move.CATCH_UP, RallyFollow.choose(false, BETWEEN, RALLIER, SLOT, UNLOCKED));
        }

        @Test
        void anUnlockedMinionWithinTheArrivalDistanceMarches() {
            assertEquals(RallyFollow.Move.MARCH, RallyFollow.choose(false, AT_THE_SLOT, RALLIER, SLOT, UNLOCKED));
        }

        @Test
        void aLockedInMinionBetweenTheArrivalAndReleaseDistancesKeepsMarching() {
            assertEquals(RallyFollow.Move.MARCH, RallyFollow.choose(false, BETWEEN, RALLIER, SLOT, LOCKED_IN));
        }

        @Test
        void aLockedInMinionBeyondTheReleaseDistanceCatchesUp() {
            assertEquals(RallyFollow.Move.CATCH_UP,
                    RallyFollow.choose(false, PAST_THE_RELEASE, RALLIER, SLOT, LOCKED_IN));
        }

        @Test
        void aMinionWithAFoeInReachFightsLockedInOrNot() {
            for (Vec3 mob : List.of(AT_THE_SLOT, BETWEEN, PAST_THE_RELEASE)) {
                assertEquals(RallyFollow.Move.FIGHT, RallyFollow.choose(true, mob, RALLIER, SLOT, UNLOCKED));
                assertEquals(RallyFollow.Move.FIGHT, RallyFollow.choose(true, mob, RALLIER, SLOT, LOCKED_IN));
            }
        }
    }

    /** rallied-mobs-share-their-ralliers-speed */
    @Nested
    class Pace {

        /** A player's walk, in blocks a tick. */
        private static final double RALLIER_WALK = 0.216;
        /** A rallied cow's movement speed: a cow's 0.2, lifted by the rally's half. */
        private static final double RALLIED_COW_SPEED = 0.3;
        /** A rallied fox's movement speed: a fox's 0.3, lifted by the rally's half. */
        private static final double RALLIED_FOX_SPEED = 0.45;
        private static final double MATCHED = 1e-9;

        private double groundSpeedAt(double ownSpeed, double rallierSpeed, boolean lockedIn) {
            return RallyFollow.groundSpeed(ownSpeed * RallyFollow.pace(rallierSpeed, ownSpeed, lockedIn));
        }

        @Test
        void aLockedInMinionSlowerThanItsRallierWalksAtTheRalliersSpeed() {
            assertTrue(RallyFollow.groundSpeed(RALLIED_COW_SPEED) < RALLIER_WALK, "the cow should be the slower");
            assertEquals(RALLIER_WALK, groundSpeedAt(RALLIED_COW_SPEED, RALLIER_WALK, LOCKED_IN), MATCHED);
        }

        @Test
        void aLockedInMinionFasterThanItsRallierWalksAtTheRalliersSpeed() {
            assertTrue(RallyFollow.groundSpeed(RALLIED_FOX_SPEED) > RALLIER_WALK, "the fox should be the faster");
            assertEquals(RALLIER_WALK, groundSpeedAt(RALLIED_FOX_SPEED, RALLIER_WALK, LOCKED_IN), MATCHED);
        }

        @Test
        void aMinionCatchingUpOutpacesItsRallier() {
            assertTrue(groundSpeedAt(RALLIED_COW_SPEED, RALLIER_WALK, UNLOCKED) > RALLIER_WALK);
            assertTrue(groundSpeedAt(RALLIED_FOX_SPEED, RALLIER_WALK, UNLOCKED) > RALLIER_WALK);
        }

        @Test
        void aMinionCatchingUpToAStandingRallierStillMoves() {
            assertTrue(RallyFollow.pace(0.0, RALLIED_COW_SPEED, UNLOCKED) > 0.0);
        }
    }

    /** slots-follow-rally-order-size-and-count */
    @Nested
    class Formation {

        @Test
        void theSameIndexCountAndWidthsAnswerTheSameSlot() {
            List<Double> widths = List.of(COW, HORSE, CHICKEN, COW, COW);
            for (int index = 0; index < widths.size(); index++) {
                assertEquals(RallyFollow.formation(widths).get(index),
                        RallyFollow.formation(new ArrayList<>(widths)).get(index), "slot " + index + " moved");
            }
        }

        @Test
        void aFormationAnswersOneSlotPerMinion() {
            assertEquals(12, RallyFollow.formation(cows(12)).size());
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
            double small = RallyFollow.formation(cows(3)).getLast().distance();
            double large = RallyFollow.formation(cows(12)).getLast().distance();
            assertTrue(large > small, "twelve cows' last slot stands at " + large + ", three cows' at " + small);
        }

        @Test
        void aSlotBesideAWiderMobStandsFartherOut() {
            double narrow = RallyFollow.formation(List.of(COW, COW, COW)).getFirst().distance();
            double wide = RallyFollow.formation(List.of(COW, HORSE, COW)).getFirst().distance();
            assertTrue(wide > narrow, "beside a horse the slot stands at " + wide + ", beside a cow at " + narrow);
        }

        private void assertEveryPairApart(List<Double> widths) {
            List<RallyFollow.Slot> slots = RallyFollow.formation(widths);
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
    class RallyOrder {

        private static final UUID LOW = new UUID(0L, 1L);
        private static final UUID MIDDLE = new UUID(0L, 2L);
        private static final UUID HIGH = new UUID(0L, 3L);

        private record Minion(long rallyOrder, UUID uuid) {
        }

        private List<Minion> ordered(List<Minion> minions) {
            return RallyFollow.inRallyOrder(minions, Minion::rallyOrder, Minion::uuid);
        }

        @Test
        void theEarliestRallyTakesIndexZeroAndLaterOnesRise() {
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
        void rallierFacingNorthAndBackingSouthPutsEveryMinionSouthOfIt() {
            // the destination reads travel alone, so a rallier facing north while it backs up heads south
            for (RallyFollow.Slot slot : RallyFollow.formation(FOUR_COWS)) {
                Vec3 spot = RallyFollow.destination(RALLIER, BACKING_SOUTH, slot).orElseThrow();
                assertTrue(spot.z > RALLIER.z + 1.0, slot + " lands at " + spot);
            }
        }

        @Test
        void rallierSteppingWestPutsEveryMinionWestOfIt() {
            for (RallyFollow.Slot slot : RallyFollow.formation(FOUR_COWS)) {
                Vec3 spot = RallyFollow.destination(RALLIER, STEPPING_WEST, slot).orElseThrow();
                assertTrue(spot.x < RALLIER.x - 1.0, slot + " lands at " + spot);
            }
        }

        @Test
        void aMinionHoldsItsSlotRelativeToTravelWhenTheRallierTurns() {
            for (RallyFollow.Slot slot : RallyFollow.formation(FOUR_COWS)) {
                Vec3 south = RallyFollow.destination(RALLIER, BACKING_SOUTH, slot).orElseThrow().subtract(RALLIER);
                Vec3 west = RallyFollow.destination(RALLIER, STEPPING_WEST, slot).orElseThrow().subtract(RALLIER);
                // the quarter turn taking south travel (0, 1) to west travel (-1, 0) takes (x, z) to (-z, x)
                assertEquals(-south.z, west.x, SAME, slot + " moved off its place under westward travel");
                assertEquals(south.x, west.z, SAME, slot + " moved off its place under westward travel");
            }
        }

        @Test
        void neighbouringSlotsStandOnEitherSideOfTheTravelLine() {
            List<RallyFollow.Slot> slots = RallyFollow.formation(List.of(COW, COW));
            Vec3 first = RallyFollow.destination(RALLIER, BACKING_SOUTH, slots.get(0)).orElseThrow();
            Vec3 second = RallyFollow.destination(RALLIER, BACKING_SOUTH, slots.get(1)).orElseThrow();
            assertTrue((first.x - RALLIER.x) * (second.x - RALLIER.x) < 0.0,
                    "two cows' slots " + first + " and " + second + " stand on one side of the travel line");
        }

        @Test
        void rallierBelowTheStandingSpeedGivesNoOffsetDestination() {
            Vec3 drift = new Vec3(0.0, 0.0, RallyFollow.STANDING_SPEED / 2);
            assertEquals(Optional.empty(), RallyFollow.destination(RALLIER, drift, LONE_COW_SLOT));
        }

        @Test
        void rallierFallingStraightDownGivesNoOffsetDestination() {
            assertEquals(Optional.empty(), RallyFollow.destination(RALLIER, new Vec3(0.0, -1.0, 0.0), LONE_COW_SLOT));
        }
    }

    @Nested
    class Spot {

        @Test
        void standingRallierKeepsTheHeldSpotNearIt() {
            assertEquals(Optional.of(HELD_SPOT),
                    RallyFollow.spot(Optional.of(HELD_SPOT), RALLIER, STANDING, LONE_COW_SLOT));
        }

        @Test
        void standingRallierKeepsAnOuterSlotsHeldSpot() {
            RallyFollow.Slot outer = RallyFollow.formation(cows(12)).getLast();
            Vec3 held = RallyFollow.destination(RALLIER, BACKING_SOUTH, outer).orElseThrow();
            assertEquals(Optional.of(held), RallyFollow.spot(Optional.of(held), RALLIER, STANDING, outer));
        }

        @Test
        void movingRallierReplacesTheHeldSpotWithAFreshOne() {
            Optional<Vec3> spot = RallyFollow.spot(Optional.of(HELD_SPOT), RALLIER, STEPPING_WEST, LONE_COW_SLOT);
            assertTrue(spot.orElseThrow().x < RALLIER.x - 1.0, "the spot stayed behind at " + spot);
        }

        @Test
        void rallierFarFromItsHeldSpotDropsIt() {
            Vec3 teleported = RALLIER.add(50.0, 0.0, 0.0);
            assertEquals(Optional.empty(),
                    RallyFollow.spot(Optional.of(HELD_SPOT), teleported, STANDING, LONE_COW_SLOT));
        }
    }

    @Nested
    class SmoothVelocity {

        @Test
        void aSingleStutterKeepsTheHeading() {
            Vec3 heading = new Vec3(0.0, 0.0, WALK);
            Vec3 smoothed = RallyFollow.smoothVelocity(heading, new Vec3(0.0, 0.0, -WALK));
            assertTrue(smoothed.z > RallyFollow.STANDING_SPEED, "the stutter swung the heading to " + smoothed);
        }

        @Test
        void steadyStepsSettleOnTheirVelocity() {
            Vec3 smoothed = Vec3.ZERO;
            for (int tick = 0; tick < 40; tick++) {
                smoothed = RallyFollow.smoothVelocity(smoothed, STEPPING_WEST);
            }
            assertEquals(-WALK, smoothed.x, 1e-3);
        }

        @Test
        void aTeleportStepCountsAsStanding() {
            Vec3 smoothed = RallyFollow.smoothVelocity(BACKING_SOUTH, new Vec3(100.0, 0.0, 0.0));
            assertEquals(Vec3.ZERO, smoothed);
        }
    }
}
