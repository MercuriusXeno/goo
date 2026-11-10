package com.mercuriusxeno.goo.ability.reap;

import com.mercuriusxeno.goo.ability.AbilityBadge;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.ability.DeliveryKind;
import com.mercuriusxeno.goo.ability.HeldRoute;
import com.mercuriusxeno.goo.ability.program.SlashStep;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers Reap's blades: each heads within a 35 degree cone of the slasher's look, bows along the way it travels, moves with the slasher's velocity, centred on the
 * aim within a block to the side and just under it, wholly below the eye line, its face tipped to show its depth, lying within ten
 * degrees of flat; it forms ahead of the eye small, shallow and to one side,
 * grows its width and its depth at their own rates in view to a crescent
 * centred on its offset, drifting on all the while; it covers the cells
 * under it and not those beside it; and the shipped Reap is channeled with
 * nothing thrown.
 * reap-is-crissaegrim-slashes-that-harvest-and-cut
 */
class ReapBladeTest {

    private static final Vec3 EYE = new Vec3(0.5, 1.62, 0.5);
    private static final Vec3 LEVEL_SOUTH = new Vec3(0, 0, 1);
    private static final ReapBlade.Shape SHAPE = new ReapBlade.Shape(0.75, 3, 2.5, 0.8, 6, 8, 3);
    private static final int SAMPLES = 2000;
    private static final double TOLERANCE = 1e-6;

    private static ReapBlade flatBlade(double offset, double lift, int startSide) {
        return new ReapBlade(EYE, LEVEL_SOUTH, new Vec3(-1, 0, 0), offset, lift, startSide, Vec3.ZERO, SHAPE);
    }

    /** How far a heading turns from the look about the vertical, in degrees. */
    private static double turnDegrees(Vec3 look, Vec3 heading) {
        Vec3 flatLook = new Vec3(look.x, 0, look.z).normalize();
        Vec3 flatHeading = new Vec3(heading.x, 0, heading.z).normalize();
        return Math.toDegrees(Math.acos(Math.clamp(flatLook.dot(flatHeading), -1, 1)));
    }

    /** The narrowest, shallowest blade shape leaf_reap.json ships. */
    private static ReapBlade.Shape shippedShape() {
        SlashStep step = (SlashStep) AbilityJson.decode("leaf_reap").behaviors().getFirst();
        return new ReapBlade.Shape(step.start().evaluate(null), step.drift().evaluate(null),
                step.width().evaluate(null), step.depth().evaluate(null), step.widthTicks().evaluateInt(null),
                step.depthTicks().evaluateInt(null), step.fadeTicks().evaluateInt(null));
    }

    @Nested
    class Launch {

        @Test
        void everyBladeHeadsWithinSeventeenAndAHalfDegreesOfTheLook() {
            RandomSource random = RandomSource.create(5L);
            Vec3 look = new Vec3(0.3, -0.5, 0.8).normalize();
            for (int i = 0; i < SAMPLES; i++) {
                Vec3 forward = ReapBlade.launch(EYE, look, Vec3.ZERO, SHAPE, random).forward();
                assertEquals(look.y, forward.y, TOLERANCE, "the heading keeps the look's pitch");
                assertTrue(turnDegrees(look, forward) <= 17.5 + TOLERANCE, "turn " + turnDegrees(look, forward));
            }
        }

        @Test
        void bladesFanAcrossTheWholeCone() {
            RandomSource random = RandomSource.create(6L);
            double widest = 0;
            for (int i = 0; i < SAMPLES; i++) {
                widest = Math.max(widest, turnDegrees(LEVEL_SOUTH, ReapBlade.launch(EYE, LEVEL_SOUTH, Vec3.ZERO, SHAPE, random).forward()));
            }
            assertTrue(widest > 15, "widest turn " + widest);
        }

        @Test
        void everyBladeBowsAlongTheWayItTravels() {
            RandomSource random = RandomSource.create(2L);
            ReapBlade.Shape wide = new ReapBlade.Shape(0.75, 3, 3.2, 1.8, 6, 8, 3);
            for (int i = 0; i < SAMPLES; i++) {
                ReapBlade blade = ReapBlade.launch(EYE, LEVEL_SOUTH, Vec3.ZERO, wide, random);
                Vec3 travel = blade.centreAt(wide.lifeTicks()).subtract(blade.centreAt(0));
                assertTrue(turnDegrees(travel, blade.depthAxis()) < 2, "bow off its travel by "
                        + turnDegrees(travel, blade.depthAxis()) + " degrees");
            }
        }

        @Test
        void aBladeMovesWithTheSlashersVelocity() {
            Vec3 running = new Vec3(0.3, 0, 0.1);
            ReapBlade still = ReapBlade.launch(EYE, LEVEL_SOUTH, Vec3.ZERO, SHAPE, RandomSource.create(4L));
            ReapBlade carried = ReapBlade.launch(EYE, LEVEL_SOUTH, running, SHAPE, RandomSource.create(4L));
            double age = SHAPE.lifeTicks();
            assertEquals(0, carried.centreAt(age).subtract(still.centreAt(age)).distanceTo(running.scale(age)),
                    TOLERANCE);
        }

        @Test
        void everyBladeStandsWithinABlockSidewaysAndJustUnderTheAim() {
            RandomSource random = RandomSource.create(7L);
            for (int i = 0; i < SAMPLES; i++) {
                ReapBlade blade = ReapBlade.launch(EYE, LEVEL_SOUTH, Vec3.ZERO, SHAPE, random);
                assertTrue(Math.abs(blade.offset()) <= 1 + TOLERANCE, "offset " + blade.offset());
                assertTrue(blade.lift() >= -0.95 - TOLERANCE && blade.lift() <= -0.85 + TOLERANCE,
                        "lift " + blade.lift());
            }
        }

        @Test
        void bladesScatterWideSideways() {
            RandomSource random = RandomSource.create(9L);
            double widest = 0;
            for (int i = 0; i < SAMPLES; i++) {
                widest = Math.max(widest, Math.abs(ReapBlade.launch(EYE, LEVEL_SOUTH, Vec3.ZERO, SHAPE, random).offset()));
            }
            assertTrue(widest > 0.9, "offset " + widest);
        }

        @Test
        void everyBladeStandsWhollyBelowTheEyeLine() {
            RandomSource random = RandomSource.create(13L);
            ReapBlade.Shape deepest = new ReapBlade.Shape(0.75, 3, 3.2, 1.8, 6, 8, 3);
            double highest = Double.NEGATIVE_INFINITY;
            for (int i = 0; i < SAMPLES; i++) {
                ReapBlade blade = ReapBlade.launch(EYE, LEVEL_SOUTH, Vec3.ZERO, deepest, random);
                for (int age = 0; age <= deepest.lifeTicks(); age++) {
                    for (Vec3 point : blade.samplesAt(age)) {
                        highest = Math.max(highest, point.y - EYE.y);
                    }
                }
            }
            assertTrue(highest < -0.2, "the highest point stands " + highest + " from the eye line");
        }

        @Test
        void everyFullGrownBladeShowsItsDepthToTheEye() {
            RandomSource random = RandomSource.create(15L);
            for (int i = 0; i < SAMPLES; i++) {
                ReapBlade blade = ReapBlade.launch(EYE, LEVEL_SOUTH, Vec3.ZERO, SHAPE, random);
                double age = SHAPE.growTicks();
                Vec3 toLead = blade.pointAt(age, 0, 1).subtract(EYE).normalize();
                Vec3 toBack = blade.pointAt(age, 0, ReapBlade.INNER_SHARE).subtract(EYE).normalize();
                double seen = Math.toDegrees(Math.acos(Math.clamp(toLead.dot(toBack), -1, 1)));
                assertTrue(seen >= 2, "the shallowest full-grown blade seen " + seen + " degrees thick");
            }
        }

        @Test
        void everyBladeLiesWithinTenDegreesOfFlat() {
            RandomSource random = RandomSource.create(11L);
            for (int i = 0; i < SAMPLES; i++) {
                Vec3 side = ReapBlade.launch(EYE, LEVEL_SOUTH, Vec3.ZERO, SHAPE, random).side();
                double tilt = Math.toDegrees(Math.asin(Math.abs(side.y)));
                assertTrue(tilt <= 10 + TOLERANCE, "tilt " + tilt);
            }
        }

        @Test
        void bladesStartOnEitherSide() {
            RandomSource random = RandomSource.create(17L);
            long right = 0;
            for (int i = 0; i < SAMPLES; i++) {
                right += ReapBlade.launch(EYE, LEVEL_SOUTH, Vec3.ZERO, SHAPE, random).startSide() > 0 ? 1 : 0;
            }
            assertTrue(right > SAMPLES / 4 && right < SAMPLES * 3 / 4, "right " + right);
        }
    }

    @Nested
    class Growth {

        private final ReapBlade blade = flatBlade(0.3, -0.2, 1);

        @Test
        void itStartsSmallAndShallow() {
            assertTrue(blade.widthAt(0) < SHAPE.width() / 4, "width " + blade.widthAt(0));
            assertTrue(blade.depthAt(0) < SHAPE.depth() / 4, "depth " + blade.depthAt(0));
        }

        @Test
        void itStartsToOneSide() {
            double lateral = blade.centreAt(0).subtract(EYE).dot(blade.side());
            assertTrue(lateral > 0.3 + SHAPE.width() / 4, "lateral " + lateral);
        }

        @Test
        void widthAndDepthGrowAtTheirOwnRates() {
            assertEquals(SHAPE.width(), blade.widthAt(SHAPE.widthTicks()), TOLERANCE);
            assertTrue(blade.depthAt(SHAPE.widthTicks()) < SHAPE.depth(), "depth " + blade.depthAt(SHAPE.widthTicks()));
            assertEquals(SHAPE.depth(), blade.depthAt(SHAPE.depthTicks()), TOLERANCE);
        }

        @Test
        void fullGrownItStandsCentredOnItsOffsetAndLift() {
            Vec3 fromEye = blade.centreAt(SHAPE.depthTicks()).subtract(EYE);
            assertEquals(0.3, fromEye.dot(blade.side()), TOLERANCE);
            assertEquals(-0.2, fromEye.y, TOLERANCE);
        }

        @Test
        void fullGrownItStillDriftsForward() {
            double grown = blade.centreAt(SHAPE.growTicks()).z;
            double drifted = blade.centreAt(SHAPE.lifeTicks()).z;
            assertTrue(drifted - grown > 0.1, "drift " + (drifted - grown));
            assertEquals(SHAPE.drift(), blade.centreAt(SHAPE.lifeTicks()).z - blade.centreAt(0).z, TOLERANCE);
        }

        @Test
        void theShippedBladeGrowsInViewForFiveTicksOrMore() {
            ReapBlade shipped = ReapBlade.launch(EYE, LEVEL_SOUTH, Vec3.ZERO, shippedShape(), RandomSource.create(1L));
            int growingInView = 0;
            for (int age = 0; age <= shipped.shape().lifeTicks(); age++) {
                double ahead = shipped.centreAt(age).subtract(EYE).dot(LEVEL_SOUTH);
                boolean growing = shipped.widthAt(age) < shipped.shape().width()
                        || shipped.depthAt(age) < shipped.shape().depth();
                growingInView += ahead >= 1 && growing ? 1 : 0;
            }
            assertTrue(growingInView >= 5, "ticks it grows a block or more ahead: " + growingInView);
        }

        @Test
        void itIsACrescentBowedAwayFromTheSlasherWithMeetingTips() {
            double age = SHAPE.lifeTicks();
            Vec3 middleLead = blade.pointAt(age, 0, 1);
            Vec3 middleBack = blade.pointAt(age, 0, ReapBlade.INNER_SHARE);
            Vec3 tipLead = blade.pointAt(age, 1, 1);
            Vec3 tipBack = blade.pointAt(age, 1, ReapBlade.INNER_SHARE);
            assertTrue(middleLead.z - middleBack.z > 0.3, "middle depth " + (middleLead.z - middleBack.z));
            assertTrue(middleLead.z > tipLead.z, "the middle bows ahead of the tips");
            assertEquals(0, tipLead.distanceTo(tipBack), TOLERANCE);
        }
    }

    @Nested
    class Cover {

        private final ReapBlade blade = new ReapBlade(new Vec3(0.5, 1.5, 0.5), LEVEL_SOUTH, new Vec3(-1, 0, 0),
                0, 0, 1, Vec3.ZERO, SHAPE);

        private boolean covers(BlockPos cell) {
            for (int age = 0; age <= SHAPE.lifeTicks(); age++) {
                if (blade.cellsAt(age).contains(cell)) {
                    return true;
                }
            }
            return false;
        }

        @Test
        void theCellsUnderTheBladeAreCovered() {
            assertTrue(covers(new BlockPos(0, 1, 2)));
            assertTrue(covers(new BlockPos(1, 1, 2)));
        }

        @Test
        void aCellBesideTheBladeIsNotCovered() {
            assertFalse(covers(new BlockPos(3, 1, 2)));
            assertFalse(covers(new BlockPos(0, 1, 6)));
        }

        @Test
        void theSamplesFillTheCrescent() {
            List<Vec3> samples = blade.samplesAt(SHAPE.lifeTicks());
            assertTrue(samples.size() > 10, "samples " + samples.size());
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
        void eachSwingThrowsOneOrTwoBlades() {
            SlashStep step = (SlashStep) reap.behaviors().getFirst();
            assertEquals(1, step.fewestBlades().evaluateInt(null));
            assertEquals(2, step.mostBlades().evaluateInt(null));
        }

        @Test
        void reapKeepsItsName() {
            assertEquals("goo.ability.leaf.reap", reap.displayName());
        }

        @Test
        void reapSlashes() {
            assertTrue(reap.behaviors().stream().anyMatch(SlashStep.class::isInstance), reap.behaviors().toString());
        }
    }
}
