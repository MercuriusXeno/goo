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
 * Covers Reap's blades: each stands centred in front of the slasher within
 * half a block sideways, lies nearly flat, and pitches between level and the
 * dive that meets the ground at its reach; it starts small, shallow and to
 * one side, grows its width and its depth at their own rates to a crescent
 * centred on its offset, then drifts on; it covers the cells under it and
 * not those beside it; and the shipped Reap is channeled with nothing thrown.
 * reap-is-crissaegrim-slashes-that-harvest-and-cut
 */
class ReapBladeTest {

    private static final double CHEST_HEIGHT = 1.08;
    private static final Vec3 CHEST = new Vec3(0.5, 1.08, 0.5);
    private static final ReapBlade.Shape SHAPE = new ReapBlade.Shape(2, 1, 2.5, 0.8, 3, 5, 3);
    private static final int SAMPLES = 2000;
    private static final double TOLERANCE = 1e-6;
    /** Due south in Minecraft's yaw, where the look runs along positive z. */
    private static final float SOUTH = 0f;

    private static ReapBlade flatBlade(double offset, int startSide) {
        return new ReapBlade(CHEST, new Vec3(0, 0, 1), new Vec3(-1, 0, 0), offset, startSide, SHAPE);
    }

    @Nested
    class Launch {

        @Test
        void everyBladeStandsWithinHalfABlockOfTheLook() {
            RandomSource random = RandomSource.create(7L);
            for (int i = 0; i < SAMPLES; i++) {
                ReapBlade blade = ReapBlade.launch(CHEST, CHEST_HEIGHT, SOUTH, SHAPE, random);
                assertTrue(Math.abs(blade.offset()) <= 0.5 + TOLERANCE, "offset " + blade.offset());
                assertEquals(0, blade.forward().x, TOLERANCE, "the heading runs along the look");
            }
        }

        @Test
        void everyBladeLiesWithinTenDegreesOfFlat() {
            RandomSource random = RandomSource.create(11L);
            for (int i = 0; i < SAMPLES; i++) {
                Vec3 side = ReapBlade.launch(CHEST, CHEST_HEIGHT, SOUTH, SHAPE, random).side();
                double tilt = Math.toDegrees(Math.asin(Math.abs(side.y)));
                assertTrue(tilt <= 10 + TOLERANCE, "tilt " + tilt);
            }
        }

        @Test
        void everyBladePitchesBetweenLevelAndTheGroundDive() {
            RandomSource random = RandomSource.create(13L);
            double limit = ReapBlade.dropLimit(CHEST_HEIGHT, SHAPE.travel() + SHAPE.drift());
            for (int i = 0; i < SAMPLES; i++) {
                Vec3 forward = ReapBlade.launch(CHEST, CHEST_HEIGHT, SOUTH, SHAPE, random).forward();
                double pitch = Math.toDegrees(Math.asin(-forward.y));
                assertTrue(pitch >= -TOLERANCE && pitch <= limit + TOLERANCE, "pitch " + pitch);
            }
        }

        @Test
        void theGroundDiveMeetsTheGroundAtTheReach() {
            double limit = Math.toRadians(ReapBlade.dropLimit(CHEST_HEIGHT, 3));
            assertEquals(CHEST_HEIGHT, 3 * Math.tan(limit), TOLERANCE);
        }

        @Test
        void bladesStartOnEitherSide() {
            RandomSource random = RandomSource.create(17L);
            long right = 0;
            for (int i = 0; i < SAMPLES; i++) {
                right += ReapBlade.launch(CHEST, CHEST_HEIGHT, SOUTH, SHAPE, random).startSide() > 0 ? 1 : 0;
            }
            assertTrue(right > SAMPLES / 4 && right < SAMPLES * 3 / 4, "right " + right);
        }
    }

    @Nested
    class Growth {

        private final ReapBlade blade = flatBlade(0.3, 1);

        @Test
        void itStartsSmallAndShallow() {
            assertTrue(blade.widthAt(0) < SHAPE.width() / 4, "width " + blade.widthAt(0));
            assertTrue(blade.depthAt(0) < SHAPE.depth() / 4, "depth " + blade.depthAt(0));
        }

        @Test
        void itStartsToOneSide() {
            double lateral = blade.centreAt(0).subtract(CHEST).dot(blade.side());
            assertTrue(lateral > 0.3 + SHAPE.width() / 4, "lateral " + lateral);
        }

        @Test
        void widthAndDepthGrowAtTheirOwnRates() {
            assertEquals(SHAPE.width(), blade.widthAt(SHAPE.widthTicks()), TOLERANCE);
            assertTrue(blade.depthAt(SHAPE.widthTicks()) < SHAPE.depth(), "depth " + blade.depthAt(SHAPE.widthTicks()));
            assertEquals(SHAPE.depth(), blade.depthAt(SHAPE.depthTicks()), TOLERANCE);
        }

        @Test
        void fullGrownItStandsCentredOnItsOffset() {
            double lateral = blade.centreAt(SHAPE.depthTicks()).subtract(CHEST).dot(blade.side());
            assertEquals(0.3, lateral, TOLERANCE);
        }

        @Test
        void fullGrownItStillDriftsForward() {
            double grown = blade.centreAt(SHAPE.depthTicks()).z;
            double drifted = blade.centreAt(SHAPE.lifeTicks()).z;
            assertEquals(SHAPE.drift(), drifted - grown, TOLERANCE);
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

        private final ReapBlade blade = new ReapBlade(new Vec3(0.5, 1.5, 0.5), new Vec3(0, 0, 1),
                new Vec3(-1, 0, 0), 0, 1, SHAPE);

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
