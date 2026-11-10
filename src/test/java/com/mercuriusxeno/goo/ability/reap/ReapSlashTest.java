package com.mercuriusxeno.goo.ability.reap;

import com.mercuriusxeno.goo.ability.AbilityBadge;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.ability.DeliveryKind;
import com.mercuriusxeno.goo.ability.HeldRoute;
import com.mercuriusxeno.goo.ability.program.SlashStep;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers Reap's slash, the Crissaegrim's from Symphony of the Night as its
 * frames show it: a huge thick crescent lying in a plane along the look,
 * hollow toward the slasher and bulging away, its tips about a block out and
 * its bulge reaching about three, rolled at random about the look and set off
 * the line of sight so it shows its face; appearing whole and dimming as it
 * drifts a little outward; the box it cuts; and the shipped Reap channeled
 * with nothing thrown.
 * reap-is-crissaegrim-slashes-that-harvest-and-cut
 */
class ReapSlashTest {

    private static final Vec3 EYE = new Vec3(0.5, 1.62, 0.5);
    private static final Vec3 LEVEL_SOUTH = new Vec3(0, 0, 1);
    private static final ReapSlash.Shape SHAPE = new ReapSlash.Shape(1, 2.5, 3.5, 0.6, 1.0, 2, 0.8, 0.4, 4);
    private static final int SAMPLES = 2000;
    private static final double TOLERANCE = 1e-6;

    private static ReapSlash slash(long seed) {
        return ReapSlash.launch(EYE, LEVEL_SOUTH, SHAPE, RandomSource.create(seed));
    }

    private static double ahead(Vec3 point) {
        return point.subtract(EYE).dot(LEVEL_SOUTH);
    }

    @Nested
    class Crescent {

        @Test
        void itLiesInAPlaneAlongTheLookSetOffTheLineOfSight() {
            for (long seed = 0; seed < 50; seed++) {
                ReapSlash slash = slash(seed);
                Vec3 normal = slash.forward().cross(slash.span()).normalize();
                assertEquals(0, slash.span().dot(LEVEL_SOUTH), TOLERANCE, "span " + slash.span());
                for (double along : new double[] {0, 0.25, 0.5, 0.75, 1}) {
                    for (double layer : new double[] {0, 1}) {
                        double offPlane = slash.pointAt(0, along, layer).subtract(EYE).dot(normal);
                        assertEquals(0.8, Math.abs(offPlane), TOLERANCE, "off the line of sight " + offPlane);
                    }
                }
            }
        }

        @Test
        void itIsHollowTowardTheSlasherWithTipsABlockOutAndItsBulgeReachingThree() {
            for (long seed = 0; seed < 50; seed++) {
                ReapSlash slash = slash(seed);
                assertEquals(1, ahead(slash.pointAt(0, 0, 1)), TOLERANCE, "near tip");
                assertEquals(1, ahead(slash.pointAt(0, 1, 1)), TOLERANCE, "far tip");
                assertEquals(3, ahead(slash.pointAt(0, 0.5, 1)), TOLERANCE, "bulge");
            }
        }

        @Test
        void itIsRolledAtRandomAboutTheLookUprightToFlat() {
            RandomSource random = RandomSource.create(3L);
            double mostUpright = 0;
            double mostFlat = 1;
            for (int i = 0; i < SAMPLES; i++) {
                double upright = Math.abs(ReapSlash.launch(EYE, LEVEL_SOUTH, SHAPE, random).span().y);
                mostUpright = Math.max(mostUpright, upright);
                mostFlat = Math.min(mostFlat, upright);
            }
            assertTrue(mostUpright > 0.98 && mostFlat < 0.05, "upright " + mostUpright + ", flat " + mostFlat);
        }

        @Test
        void itsSizeRunsFromTwoAndAHalfToThreeAndAHalfAcrossThickeningWithIt() {
            RandomSource random = RandomSource.create(5L);
            double narrowest = Double.MAX_VALUE;
            double widest = 0;
            for (int i = 0; i < SAMPLES; i++) {
                ReapSlash slash = ReapSlash.launch(EYE, LEVEL_SOUTH, SHAPE, random);
                double width = slash.pointAt(0, 0, 1).distanceTo(slash.pointAt(0, 1, 1));
                double thickness = slash.pointAt(0, 0.5, 1).distanceTo(slash.pointAt(0, 0.5, 0));
                assertTrue(width >= 2.5 - TOLERANCE && width <= 3.5 + TOLERANCE, "width " + width);
                assertEquals(width - 2.5, (thickness - 0.6) / 0.4, 0.001, "width " + width + ", thick " + thickness);
                narrowest = Math.min(narrowest, width);
                widest = Math.max(widest, width);
            }
            assertTrue(narrowest < 2.6 && widest > 3.4, "sizes " + narrowest + " to " + widest);
        }

        @Test
        void itIsThickestMidArcAndItsTipsMeet() {
            ReapSlash slash = slash(11L);
            assertEquals(0, slash.pointAt(0, 0, 1).distanceTo(slash.pointAt(0, 0, 0)), TOLERANCE);
            assertEquals(0, slash.pointAt(0, 1, 1).distanceTo(slash.pointAt(0, 1, 0)), TOLERANCE);
            double middle = slash.pointAt(0, 0.5, 1).distanceTo(slash.pointAt(0, 0.5, 0));
            assertTrue(slash.pointAt(0, 0.25, 1).distanceTo(slash.pointAt(0, 0.25, 0)) < middle);
        }
    }

    @Nested
    class Life {

        private final ReapSlash slash = slash(17L);

        @Test
        void itAppearsWholeAndBrightThenDimsAway() {
            assertEquals(1, slash.strengthAt(0), TOLERANCE);
            assertTrue(slash.strengthAt(2) < slash.strengthAt(1), "dimming");
            assertEquals(0, slash.strengthAt(SHAPE.lifeTicks()), TOLERANCE);
        }

        @Test
        void itDriftsALittleOutwardAsItDims() {
            double drifted = ahead(slash.pointAt(SHAPE.lifeTicks(), 0.5, 1)) - ahead(slash.pointAt(0, 0.5, 1));
            assertEquals(0.4, drifted, TOLERANCE);
        }
    }

    @Nested
    class Box {

        @Test
        void itHoldsTheCrescentAndNothingBehindTheSlasher() {
            ReapSlash slash = slash(19L);
            AABB box = slash.box();
            assertTrue(box.contains(slash.pointAt(0, 0.5, 0.5)), "the crescent's middle");
            assertTrue(box.contains(slash.pointAt(SHAPE.lifeTicks(), 0.5, 1)), "its bulge once drifted");
            assertFalse(box.contains(EYE.add(0, 0, -1)), "behind the slasher");
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
        void reapSlashesEveryTwoOrThreeTicks() {
            SlashStep step = (SlashStep) reap.behaviors().getFirst();
            assertEquals(2, step.fewestTicks().evaluateInt(null));
            assertEquals(3, step.mostTicks().evaluateInt(null));
        }
    }
}
