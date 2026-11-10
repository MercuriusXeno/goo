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
 * Covers Reap's blades: each flies along the slasher's look, centred on the
 * aim within a block to the side and a sliver up or down, lying within ten
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
        return new ReapBlade(EYE, LEVEL_SOUTH, new Vec3(-1, 0, 0), offset, lift, startSide, SHAPE);
    }

    /** The blade shape leaf_reap.json ships. */
    private static ReapBlade.Shape shippedShape() {
        SlashStep step = (SlashStep) AbilityJson.decode("leaf_reap").behaviors().getFirst();
        return new ReapBlade.Shape(step.start().evaluate(null), step.drift().evaluate(null),
                step.width().evaluate(null), step.depth().evaluate(null), step.widthTicks().evaluateInt(null),
                step.depthTicks().evaluateInt(null), step.fadeTicks().evaluateInt(null));
    }

    @Nested
    class Launch {

        @Test
        void everyBladeFliesAlongTheLook() {
            RandomSource random = RandomSource.create(5L);
            Vec3 look = new Vec3(0.3, -0.5, 0.8).normalize();
            for (int i = 0; i < SAMPLES; i++) {
                Vec3 forward = ReapBlade.launch(EYE, look, SHAPE, random).forward();
                assertEquals(0, forward.distanceTo(look), TOLERANCE, "forward " + forward);
            }
        }

        @Test
        void everyBladeStandsWithinABlockSidewaysAndASliverUpOrDown() {
            RandomSource random = RandomSource.create(7L);
            for (int i = 0; i < SAMPLES; i++) {
                ReapBlade blade = ReapBlade.launch(EYE, LEVEL_SOUTH, SHAPE, random);
                assertTrue(Math.abs(blade.offset()) <= 1 + TOLERANCE, "offset " + blade.offset());
                assertTrue(Math.abs(blade.lift()) <= 0.15 + TOLERANCE, "lift " + blade.lift());
            }
        }

        @Test
        void bladesScatterWideSidewaysAndLittleUpAndDown() {
            RandomSource random = RandomSource.create(9L);
            double highest = 0;
            double widest = 0;
            for (int i = 0; i < SAMPLES; i++) {
                ReapBlade blade = ReapBlade.launch(EYE, LEVEL_SOUTH, SHAPE, random);
                highest = Math.max(highest, Math.abs(blade.lift()));
                widest = Math.max(widest, Math.abs(blade.offset()));
            }
            assertTrue(widest > 0.9, "offset " + widest);
            assertTrue(highest > 0.1, "lift " + highest);
        }

        @Test
        void everyBladeLiesWithinTenDegreesOfFlat() {
            RandomSource random = RandomSource.create(11L);
            for (int i = 0; i < SAMPLES; i++) {
                Vec3 side = ReapBlade.launch(EYE, LEVEL_SOUTH, SHAPE, random).side();
                double tilt = Math.toDegrees(Math.asin(Math.abs(side.y)));
                assertTrue(tilt <= 10 + TOLERANCE, "tilt " + tilt);
            }
        }

        @Test
        void bladesStartOnEitherSide() {
            RandomSource random = RandomSource.create(17L);
            long right = 0;
            for (int i = 0; i < SAMPLES; i++) {
                right += ReapBlade.launch(EYE, LEVEL_SOUTH, SHAPE, random).startSide() > 0 ? 1 : 0;
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
            ReapBlade shipped = ReapBlade.launch(EYE, LEVEL_SOUTH, shippedShape(), RandomSource.create(1L));
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
                0, 0, 1, SHAPE);

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
        void reapKeepsItsName() {
            assertEquals("goo.ability.leaf.reap", reap.displayName());
        }

        @Test
        void reapSlashes() {
            assertTrue(reap.behaviors().stream().anyMatch(SlashStep.class::isInstance), reap.behaviors().toString());
        }
    }
}
