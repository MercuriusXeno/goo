package com.mercuriusxeno.goo.ability.zoo;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers what a rallied mob does each tick: fights a foe in reach; with none,
 * takes the side its moving rallier heads toward, or walks to a standing
 * rallier from beyond the near distance and holds within it.
 */
class RallyFollowTest {

    private static final double BEYOND = RallyFollow.NEAR_DISTANCE + 1.0;
    private static final double WITHIN = RallyFollow.NEAR_DISTANCE - 1.0;
    private static final Vec3 RALLIER = new Vec3(10.5, 64.0, -3.5);
    private static final double WALK = 0.2;
    /** South is +z, so a rallier facing north and backing up moves along +z. */
    private static final Vec3 BACKING_SOUTH = new Vec3(0.0, 0.0, WALK);
    private static final Vec3 STEPPING_WEST = new Vec3(-WALK, 0.0, 0.0);
    private static final Vec3 STANDING = Vec3.ZERO;
    private static final int SPREAD_SEEDS = 12;
    /** Two minions' spots closer than this would stack them on one block. */
    private static final double APART = 0.75;

    @Nested
    class Choose {

        @Test
        void mobBeyondTheNearDistanceWalksToItsStandingRallier() {
            assertEquals(RallyFollow.Move.FOLLOW, RallyFollow.choose(false, BEYOND * BEYOND, STANDING));
        }

        @Test
        void mobWithinTheNearDistanceOfAStandingRallierHolds() {
            assertEquals(RallyFollow.Move.HOLD, RallyFollow.choose(false, WITHIN * WITHIN, STANDING));
        }

        @Test
        void mobBesideARallierBelowTheStandingSpeedHolds() {
            Vec3 drift = new Vec3(RallyFollow.STANDING_SPEED / 2, 0.0, 0.0);
            assertEquals(RallyFollow.Move.HOLD, RallyFollow.choose(false, WITHIN * WITHIN, drift));
        }

        @Test
        void mobWithAFoeInReachFightsWhereverItsRallierStands() {
            assertEquals(RallyFollow.Move.FIGHT, RallyFollow.choose(true, BEYOND * BEYOND, STANDING));
            assertEquals(RallyFollow.Move.FIGHT, RallyFollow.choose(true, WITHIN * WITHIN, STANDING));
        }

        @Test
        void mobWithAFoeInReachFightsWhileItsRallierMoves() {
            assertEquals(RallyFollow.Move.FIGHT, RallyFollow.choose(true, WITHIN * WITHIN, BACKING_SOUTH));
            assertEquals(RallyFollow.Move.FIGHT, RallyFollow.choose(true, BEYOND * BEYOND, STEPPING_WEST));
        }

        @Test
        void mobWithNoFoeTakesTheSideItsMovingRallierHeadsToward() {
            assertEquals(RallyFollow.Move.PRE_EMPT, RallyFollow.choose(false, WITHIN * WITHIN, BACKING_SOUTH));
            assertEquals(RallyFollow.Move.PRE_EMPT, RallyFollow.choose(false, BEYOND * BEYOND, STEPPING_WEST));
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
