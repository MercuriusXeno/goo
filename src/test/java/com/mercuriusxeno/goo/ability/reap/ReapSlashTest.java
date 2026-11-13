package com.mercuriusxeno.goo.ability.reap;

import com.mercuriusxeno.goo.ability.AbilityBadge;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.ability.DeliveryKind;
import com.mercuriusxeno.goo.ability.HeldRoute;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers Reap's slash as the operator drew it: a level, almost circular area
 * 5.3 blocks wide reaching 5.8 ahead with the slasher at its near edge, and a
 * crescent band rippling outward through it, its back and front circles about
 * a point half a block ahead, clipped at its sides by the area and ending as
 * the cap at the far edge; each slash nudged, leant and turned a little its
 * own way and moving with its slasher; a body reached once the band's front
 * gets to it; and the shipped Reap channeled with nothing thrown.
 * reap-is-crissaegrim-slashes-that-harvest-and-cut
 */
class ReapSlashTest {

    private static final double TOLERANCE = 1e-6;
    private static final int SAMPLES = 2000;
    private static final Vec3 SOUTH = new Vec3(0, 0, 1);
    private static final Vec3 FEET = new Vec3(10, 64, 20);
    private static final ReapSlash.Shape SHAPE = new ReapSlash.Shape(2.65, 2.9, 0.5, 1.1, 3.9, 2.6, 6);
    private static final ReapSlash.Thrown THROWN = new ReapSlash.Thrown(0.45, 0.2, Math.toRadians(10),
            Math.toRadians(5));
    private static final double EYE_HEIGHT = 1.62;
    private static final ReapSlash LEVEL = new ReapSlash(Vec3.ZERO, SOUTH, 0, 1.17, SHAPE);

    private static double inArea(double across, double out) {
        return Math.pow(across / 2.65, 2) + Math.pow((out - 2.9) / 2.9, 2);
    }

    @Nested
    class Area {

        @Test
        void itsNearEdgeStandsAtTheSlasherAndItsFarEdgeFivePointEightAhead() {
            assertEquals(0.5, SHAPE.edgeAlong(Math.PI), TOLERANCE, "from the source back to the slasher");
            assertEquals(5.3, SHAPE.edgeAlong(0), TOLERANCE, "from the source to the far edge, 5.8 ahead");
        }

        @Test
        void itIsFivePointThreeWide() {
            double widest = 0;
            for (int step = 0; step <= 3600; step++) {
                double angle = Math.toRadians(step / 20.0);
                widest = Math.max(widest, SHAPE.edgeAlong(angle) * Math.sin(angle));
            }
            assertEquals(2.65, widest, 1e-4);
        }
    }

    @Nested
    class Band {

        @Test
        void itsBackAndFrontRunFromTheFirstLinesToTheLastThroughItsLife() {
            assertEquals(1.1, SHAPE.bandAt(0).back(), TOLERANCE);
            assertEquals(2.6, SHAPE.bandAt(0).front(), TOLERANCE);
            assertEquals(2.5, SHAPE.bandAt(3).back(), TOLERANCE);
            assertEquals(3.95, SHAPE.bandAt(3).front(), TOLERANCE);
            assertEquals(3.9, SHAPE.bandAt(6).back(), TOLERANCE);
            assertEquals(5.3, SHAPE.bandAt(6).front(), TOLERANCE);
        }

        @Test
        void itsMiddleBowsAwayOnCirclesAboutItsSource() {
            ReapSlash.Band band = SHAPE.bandAt(0);
            for (double along : new double[] {0.4, 0.45, 0.5, 0.55, 0.6}) {
                ReapSlash.PlanePoint back = band.at(along, 0);
                ReapSlash.PlanePoint front = band.at(along, 1);
                assertEquals(1.1, Math.hypot(back.across(), back.out() - 0.5), TOLERANCE, "back at " + along);
                assertEquals(2.6, Math.hypot(front.across(), front.out() - 0.5), TOLERANCE, "front at " + along);
            }
            assertEquals(3.1, band.at(0.5, 1).out(), TOLERANCE, "the front's middle, 3.1 ahead");
            assertEquals(1.6, band.at(0.5, 0).out(), TOLERANCE, "the back's middle, 1.6 ahead");
        }

        @Test
        void itIsClippedAtItsSidesByTheAreaAndItsTipsPinchOut() {
            for (double age : new double[] {0, 1.5, 3, 4.5, 6}) {
                ReapSlash.Band band = SHAPE.bandAt(age);
                for (int step = 0; step <= 40; step++) {
                    for (double layer : new double[] {0, 0.5, 1}) {
                        ReapSlash.PlanePoint point = band.at(step / 40.0, layer);
                        assertTrue(inArea(point.across(), point.out()) <= 1 + 1e-6, "inside the area at age " + age);
                    }
                }
                for (double tip : new double[] {0, 1}) {
                    ReapSlash.PlanePoint back = band.at(tip, 0);
                    ReapSlash.PlanePoint front = band.at(tip, 1);
                    assertEquals(0, Math.hypot(back.across() - front.across(), back.out() - front.out()), 1e-4);
                    assertEquals(1, inArea(back.across(), back.out()), 1e-4, "the tip stands on the area's edge");
                }
            }
        }

        @Test
        void itEndsAsTheCapAtTheFarEdge() {
            ReapSlash.Band band = SHAPE.bandAt(6);
            assertEquals(5.8, band.at(0.5, 1).out(), TOLERANCE);
            for (double along : new double[] {0.1, 0.3, 0.7, 0.9}) {
                ReapSlash.PlanePoint front = band.at(along, 1);
                assertEquals(1, inArea(front.across(), front.out()), 1e-6, "its front is the area's edge");
            }
            assertEquals(4.4, band.at(0.5, 0).out(), TOLERANCE, "its back's middle, 4.4 ahead");
        }
    }

    @Nested
    class Launch {

        @Test
        void everySlashLiesLevelTurnedItsOwnWayWithinFiveDegrees() {
            RandomSource random = RandomSource.create(1L);
            boolean left = false;
            boolean right = false;
            for (int i = 0; i < SAMPLES; i++) {
                ReapSlash slash = ReapSlash.launch(SOUTH, EYE_HEIGHT, THROWN, SHAPE, random);
                assertEquals(0, slash.heading().y, TOLERANCE, "level");
                double turn = Math.toDegrees(Math.atan2(slash.heading().x, slash.heading().z));
                assertTrue(Math.abs(turn) <= 5 + TOLERANCE, "turn " + turn);
                left |= turn > 4;
                right |= turn < -4;
            }
            assertTrue(left && right, "it turns both ways");
        }

        @Test
        void everySlashLeansItsOwnWayWithinTenDegrees() {
            RandomSource random = RandomSource.create(2L);
            boolean oneWay = false;
            boolean theOther = false;
            for (int i = 0; i < SAMPLES; i++) {
                double roll = Math.toDegrees(ReapSlash.launch(SOUTH, EYE_HEIGHT, THROWN, SHAPE, random).roll());
                assertTrue(Math.abs(roll) <= 10 + TOLERANCE, "roll " + roll);
                oneWay |= roll > 9;
                theOther |= roll < -9;
            }
            assertTrue(oneWay && theOther, "it leans both ways");
        }

        @Test
        void everySlashIsNudgedWithinAFifthOfABlockEachWay() {
            RandomSource random = RandomSource.create(3L);
            Vec3 least = new Vec3(1, 1, 1);
            Vec3 most = new Vec3(-1, -1, -1);
            for (int i = 0; i < SAMPLES; i++) {
                Vec3 nudge = ReapSlash.launch(SOUTH, EYE_HEIGHT, THROWN, SHAPE, random).nudge();
                assertTrue(Math.abs(nudge.x) <= 0.2 && Math.abs(nudge.y) <= 0.2 && Math.abs(nudge.z) <= 0.2, "" + nudge);
                least = new Vec3(Math.min(least.x, nudge.x), Math.min(least.y, nudge.y), Math.min(least.z, nudge.z));
                most = new Vec3(Math.max(most.x, nudge.x), Math.max(most.y, nudge.y), Math.max(most.z, nudge.z));
            }
            assertTrue(least.x < -0.18 && least.y < -0.18 && least.z < -0.18, "least " + least);
            assertTrue(most.x > 0.18 && most.y > 0.18 && most.z > 0.18, "most " + most);
        }

        @Test
        void everySlashCoversTheSameAreaUnderTheEye() {
            ReapSlash slash = ReapSlash.launch(SOUTH, EYE_HEIGHT, THROWN, SHAPE, RandomSource.create(4L));
            assertEquals(SHAPE, slash.shape());
            assertEquals(EYE_HEIGHT - 0.45, slash.height(), TOLERANCE);
        }
    }

    @Nested
    class InTheWorld {

        @Test
        void itStandsAheadOfItsSlasherAtItsPlane() {
            Vec3 front = LEVEL.pointAt(FEET, LEVEL.height(), 0, 0.5, 1);
            assertEquals(FEET.add(0, 1.17, 3.1).x, front.x, TOLERANCE);
            assertEquals(FEET.y + 1.17, front.y, TOLERANCE);
            assertEquals(FEET.z + 3.1, front.z, TOLERANCE);
        }

        @Test
        void itMovesWithItsSlasher() {
            Vec3 moved = FEET.add(1.5, 0.5, -2);
            Vec3 before = LEVEL.pointAt(FEET, LEVEL.height(), 2, 0.3, 0.7);
            Vec3 after = LEVEL.pointAt(moved, LEVEL.height(), 2, 0.3, 0.7);
            assertEquals(0, after.subtract(before).distanceTo(new Vec3(1.5, 0.5, -2)), TOLERANCE);
        }

        @Test
        void drawnAtTheFeetItLiesOnTheGroundUnderItsSlasher() {
            for (double along : new double[] {0.1, 0.5, 0.9}) {
                assertEquals(FEET.y, LEVEL.pointAt(FEET, 0, 3, along, 1).y, TOLERANCE);
            }
        }

        @Test
        void skimmingTheGroundItsLowestEdgeClearsItHoweverItLeansAndIsNudged() {
            ReapSlash sunk = new ReapSlash(new Vec3(0, -0.2, 0), SOUTH, Math.toRadians(-10), 1.17, SHAPE);
            double plane = sunk.skimming(0.05);
            assertEquals(0.05 + 0.2 + 2.65 * Math.sin(Math.toRadians(10)), plane, TOLERANCE);
            double lowest = Double.MAX_VALUE;
            for (double age : new double[] {0, 2, 4, 6}) {
                for (int step = 0; step <= 40; step++) {
                    for (double layer : new double[] {0, 1}) {
                        lowest = Math.min(lowest, sunk.pointAt(FEET, plane, age, step / 40.0, layer).y - FEET.y);
                    }
                }
            }
            assertTrue(lowest >= 0.05 - TOLERANCE, "its lowest point stands " + lowest + " over the ground");
        }

        @Test
        void skimmingTheGroundALevelSlashLiesJustOverItAndARaisedOneStaysWhereItIs() {
            assertEquals(0.05, LEVEL.skimming(0.05), TOLERANCE);
            ReapSlash raised = new ReapSlash(new Vec3(0, 0.2, 0), SOUTH, 0, 1.17, SHAPE);
            assertEquals(0, raised.skimming(0.05), TOLERANCE);
        }

        @Test
        void itsLeanRaisesOneSideAndLowersTheOther() {
            ReapSlash leant = new ReapSlash(Vec3.ZERO, SOUTH, Math.toRadians(10), 1.17, SHAPE);
            double rightSide = leant.pointAt(FEET, 1.17, 0, 0.9, 1).y - (FEET.y + 1.17);
            double leftSide = leant.pointAt(FEET, 1.17, 0, 0.1, 1).y - (FEET.y + 1.17);
            assertTrue(rightSide > 0.1 && leftSide < -0.1, "right " + rightSide + ", left " + leftSide);
            assertEquals(FEET.y + 1.17, leant.pointAt(FEET, 1.17, 0, 0.5, 1).y, TOLERANCE, "its middle stays put");
        }

        @Test
        void itsNudgeShiftsTheWholeOfIt() {
            ReapSlash nudged = new ReapSlash(new Vec3(0.2, -0.1, 0.15), SOUTH, 0, 1.17, SHAPE);
            Vec3 shift = nudged.pointAt(FEET, 1.17, 1, 0.4, 0.5).subtract(LEVEL.pointAt(FEET, 1.17, 1, 0.4, 0.5));
            Vec3 rightOfSouth = new Vec3(-1, 0, 0);
            assertEquals(0, shift.distanceTo(rightOfSouth.scale(0.2).add(0, -0.1, 0.15)), TOLERANCE);
        }

        @Test
        void itDimsEvenlyToNothing() {
            assertEquals(1, LEVEL.strengthAt(0), TOLERANCE);
            assertEquals(0.5, LEVEL.strengthAt(3), TOLERANCE);
            assertEquals(0, LEVEL.strengthAt(6), TOLERANCE);
        }
    }

    @Nested
    class Reach {

        @Test
        void aBodyIsReachedOnceTheFrontGetsToIt() {
            Vec3 body = FEET.add(0, 0, 4.2);
            assertFalse(LEVEL.reaches(FEET, body, 0.3, 0), "the front stands 3.1 ahead as it appears");
            assertFalse(LEVEL.reaches(FEET, body, 0.3, 1));
            assertTrue(LEVEL.reaches(FEET, body, 0.3, 3), "the front stands 4.45 ahead half way");
        }

        @Test
        void aBodyRightBesideTheSlasherIsReachedAtOnce() {
            assertTrue(LEVEL.reaches(FEET, FEET.add(0.2, 0, 0.8), 0.3, 0));
        }

        @Test
        void nothingPastTheFarEdgeOrOutsideTheAreaIsEverReached() {
            for (double age : new double[] {0, 3, 6}) {
                assertFalse(LEVEL.reaches(FEET, FEET.add(0, 0, 6.3), 0.3, age), "past the far edge at age " + age);
                assertFalse(LEVEL.reaches(FEET, FEET.add(3.2, 0, 2.9), 0.3, age), "off to the side at age " + age);
                assertFalse(LEVEL.reaches(FEET, FEET.add(0, 0, -0.6), 0.3, age), "behind the slasher at age " + age);
            }
            assertTrue(LEVEL.reaches(FEET, FEET.add(0, 0, 5.6), 0.3, 6), "just inside the far edge at the end");
        }

        @Test
        void aWiderBodyIsReachedFromFartherOut() {
            Vec3 beside = FEET.add(3.0, 0, 2.9);
            assertFalse(LEVEL.reaches(FEET, beside, 0.2, 6));
            assertTrue(LEVEL.reaches(FEET, beside, 0.5, 6));
        }

        @Test
        void itTurnsWithItsHeading() {
            ReapSlash east = new ReapSlash(Vec3.ZERO, new Vec3(1, 0, 0), 0, 1.17, SHAPE);
            assertTrue(east.reaches(FEET, FEET.add(4, 0, 0), 0.3, 6));
            assertFalse(east.reaches(FEET, FEET.add(0, 0, 4), 0.3, 6));
        }

        @Test
        void itsReachBoxHoldsTheAreaAtItsSlashersLevel() {
            AABB box = LEVEL.reachBox(FEET);
            assertTrue(box.contains(FEET.add(0, 0.5, 5.7)), "the far edge");
            assertTrue(box.contains(FEET.add(2.6, 0.5, 2.9)), "the widest point");
            assertTrue(box.contains(FEET.add(0, -0.4, 1)) && box.contains(FEET.add(0, 1.6, 1)), "feet to head");
            assertFalse(box.contains(FEET.add(0, 3, 1)), "well above");
            assertFalse(box.contains(FEET.add(0, -2, 1)), "well below");
        }
    }

    @Nested
    class ShippedReap {

        private final AbilityDefinition reap = AbilityJson.decode("leaf_reap");

        @Test
        void reapIsChanneledAndThrowsNothing() {
            assertEquals(AbilityBadge.CHANNELED, reap.badge());
            assertEquals(DeliveryKind.SELF, reap.delivery().kind());
            assertTrue(HeldRoute.channelsOnSelf(reap.delivery(), reap.badge()));
            assertFalse(reap.tags().contains("place_block"));
        }

        @Test
        void reapKeepsItsName() {
            assertEquals("goo.ability.leaf.reap", reap.displayName());
        }

    }
}
