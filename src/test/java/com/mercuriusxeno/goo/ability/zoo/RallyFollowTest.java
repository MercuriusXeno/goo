package com.mercuriusxeno.goo.ability.zoo;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers what a rallied mob does each tick: fights a foe in reach; with none,
 * walks to the spot on the side its rallier heads toward and holds there once
 * the rallier stops, or walks to a rallier it holds no spot for from beyond
 * the near distance and holds within it.
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
    private static final int SPREAD_SEEDS = 12;
    /** Two minions' spots closer than this would stack them on one block. */
    private static final double APART = 0.75;
    /** A spot three blocks behind a rallier that backed up south and stopped. */
    private static final Vec3 HELD_SPOT = RALLIER.add(0.0, 0.0, RallyFollow.LEAD_DISTANCE);

    private static Optional<Vec3> movingSpot(Vec3 velocity) {
        return RallyFollow.spot(Optional.empty(), RALLIER, velocity, 0);
    }

    @Nested
    class Choose {

        @Test
        void mobBeyondTheNearDistanceWalksToItsStandingRallier() {
            assertEquals(RallyFollow.Move.FOLLOW, RallyFollow.choose(false, BEYOND, RALLIER, Optional.empty()));
        }

        @Test
        void mobWithinTheNearDistanceOfAStandingRallierHolds() {
            assertEquals(RallyFollow.Move.HOLD, RallyFollow.choose(false, WITHIN, RALLIER, Optional.empty()));
        }

        @Test
        void mobBesideARallierBelowTheStandingSpeedHolds() {
            Vec3 drift = new Vec3(RallyFollow.STANDING_SPEED / 2, 0.0, 0.0);
            Optional<Vec3> spot = RallyFollow.spot(Optional.empty(), RALLIER, drift, 0);
            assertEquals(RallyFollow.Move.HOLD, RallyFollow.choose(false, WITHIN, RALLIER, spot));
        }

        @Test
        void mobWithAFoeInReachFightsWhereverItsRallierStands() {
            assertEquals(RallyFollow.Move.FIGHT, RallyFollow.choose(true, BEYOND, RALLIER, Optional.empty()));
            assertEquals(RallyFollow.Move.FIGHT, RallyFollow.choose(true, WITHIN, RALLIER, Optional.empty()));
        }

        @Test
        void mobWithAFoeInReachFightsWhileItsRallierMoves() {
            assertEquals(RallyFollow.Move.FIGHT,
                    RallyFollow.choose(true, WITHIN, RALLIER, movingSpot(BACKING_SOUTH)));
            assertEquals(RallyFollow.Move.FIGHT,
                    RallyFollow.choose(true, BEYOND, RALLIER, movingSpot(STEPPING_WEST)));
        }

        @Test
        void mobWithNoFoeTakesTheSideItsMovingRallierHeadsToward() {
            assertEquals(RallyFollow.Move.PRE_EMPT,
                    RallyFollow.choose(false, WITHIN, RALLIER, movingSpot(BACKING_SOUTH)));
            assertEquals(RallyFollow.Move.PRE_EMPT,
                    RallyFollow.choose(false, BEYOND, RALLIER, movingSpot(STEPPING_WEST)));
        }

        @Test
        void mobShortOfItsHeldSpotKeepsWalkingThereOnceItsRallierStops() {
            Optional<Vec3> spot = RallyFollow.spot(Optional.of(HELD_SPOT), RALLIER, STANDING, 0);
            assertEquals(RallyFollow.Move.PRE_EMPT, RallyFollow.choose(false, RALLIER, RALLIER, spot));
        }

        @Test
        void mobAtItsHeldSpotHoldsThere() {
            Optional<Vec3> spot = RallyFollow.spot(Optional.of(HELD_SPOT), RALLIER, STANDING, 0);
            Vec3 arrived = HELD_SPOT.add(RallyFollow.ARRIVAL_DISTANCE / 2, 0.0, 0.0);
            assertEquals(RallyFollow.Move.HOLD, RallyFollow.choose(false, arrived, RALLIER, spot));
        }
    }

    @Nested
    class Destination {

        @Test
        void rallierFacingNorthAndBackingSouthPutsEveryMinionSouthOfIt() {
            // the destination reads travel alone, so a rallier facing north while it backs up heads south
            for (int seed = 0; seed < SPREAD_SEEDS; seed++) {
                Vec3 spot = RallyFollow.destination(RALLIER, BACKING_SOUTH, seed).orElseThrow();
                assertTrue(spot.z > RALLIER.z + 1.0, "seed " + seed + " lands at " + spot);
            }
        }

        @Test
        void rallierSteppingWestPutsEveryMinionWestOfIt() {
            for (int seed = 0; seed < SPREAD_SEEDS; seed++) {
                Vec3 spot = RallyFollow.destination(RALLIER, STEPPING_WEST, seed).orElseThrow();
                assertTrue(spot.x < RALLIER.x - 1.0, "seed " + seed + " lands at " + spot);
            }
        }

        @Test
        void rallierBelowTheStandingSpeedGivesNoOffsetDestination() {
            Vec3 drift = new Vec3(0.0, 0.0, RallyFollow.STANDING_SPEED / 2);
            assertEquals(Optional.empty(), RallyFollow.destination(RALLIER, drift, 0));
        }

        @Test
        void rallierFallingStraightDownGivesNoOffsetDestination() {
            assertEquals(Optional.empty(), RallyFollow.destination(RALLIER, new Vec3(0.0, -1.0, 0.0), 0));
        }

        @Test
        void minionsSpreadAroundTheDestinationRatherThanStack() {
            for (int one = 0; one < RallyFollow.SPREAD_ROUND; one++) {
                for (int other = one + 1; other < RallyFollow.SPREAD_ROUND; other++) {
                    Vec3 first = RallyFollow.destination(RALLIER, BACKING_SOUTH, one).orElseThrow();
                    Vec3 second = RallyFollow.destination(RALLIER, BACKING_SOUTH, other).orElseThrow();
                    assertTrue(first.distanceTo(second) > APART, "seeds " + one + " and " + other + " stack");
                }
            }
        }
    }

    @Nested
    class Spot {

        @Test
        void standingRallierKeepsTheHeldSpotNearIt() {
            assertEquals(Optional.of(HELD_SPOT), RallyFollow.spot(Optional.of(HELD_SPOT), RALLIER, STANDING, 0));
        }

        @Test
        void movingRallierReplacesTheHeldSpotWithAFreshOne() {
            Optional<Vec3> spot = RallyFollow.spot(Optional.of(HELD_SPOT), RALLIER, STEPPING_WEST, 0);
            assertTrue(spot.orElseThrow().x < RALLIER.x - 1.0, "the spot stayed behind at " + spot);
        }

        @Test
        void rallierFarFromItsHeldSpotDropsIt() {
            Vec3 teleported = RALLIER.add(50.0, 0.0, 0.0);
            assertEquals(Optional.empty(), RallyFollow.spot(Optional.of(HELD_SPOT), teleported, STANDING, 0));
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
