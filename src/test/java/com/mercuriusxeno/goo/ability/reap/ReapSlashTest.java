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
 * frames show it: a thick crescent lying nearly flat in a plane along the
 * look, wholly below the eye line, hollow toward the slasher and bulging
 * away, its tips about a block out and its bulge growing with its size, which
 * runs from small to huge with its thickness, as often small or huge as middling;
 * appearing whole, then shrinking to nothing as it travels outward, a thinner
 * outer blade running out ahead of it; the box it cuts; and the shipped Reap channeled
 * with nothing thrown.
 * reap-is-crissaegrim-slashes-that-harvest-and-cut
 */
class ReapSlashTest {

    private static final Vec3 EYE = new Vec3(0.5, 1.62, 0.5);
    private static final Vec3 LEVEL_SOUTH = new Vec3(0, 0, 1);
    private static final ReapSlash.Shape SHAPE = new ReapSlash.Shape(1, 0.8, 6, 0.3, 2.0, 0.55, 0.6, 1.0,
            Math.toRadians(10), 1.5, 2.5, 6);
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
        void itLiesAlongTheLookSetUnderTheLineOfSight() {
            RandomSource random = RandomSource.create(1L);
            double lowest = Double.MAX_VALUE;
            double highest = 0;
            for (int i = 0; i < SAMPLES; i++) {
                ReapSlash slash = ReapSlash.launch(EYE, LEVEL_SOUTH, SHAPE, random);
                Vec3 normal = slash.forward().cross(slash.span()).normalize();
                assertEquals(0, slash.span().dot(LEVEL_SOUTH), TOLERANCE, "span " + slash.span());
                double offPlane = slash.pointAt(0, 0.5, 1).subtract(EYE).dot(normal);
                double under = Math.abs(offPlane);
                assertTrue(under >= 0.6 - TOLERANCE && under <= 1.0 + TOLERANCE, "under " + under);
                assertTrue(normal.scale(offPlane).y < 0, "it sits under the line of sight");
                lowest = Math.min(lowest, under);
                highest = Math.max(highest, under);
            }
            assertTrue(lowest < 0.65 && highest > 0.95, "drops " + lowest + " to " + highest);
        }

        @Test
        void itLiesWithinTenDegreesOfFlat() {
            RandomSource random = RandomSource.create(3L);
            double steepest = 0;
            for (int i = 0; i < SAMPLES; i++) {
                double tilt = Math.toDegrees(Math.asin(Math.abs(ReapSlash.launch(EYE, LEVEL_SOUTH, SHAPE, random).span().y)));
                assertTrue(tilt <= 10 + TOLERANCE, "tilt " + tilt);
                steepest = Math.max(steepest, tilt);
            }
            assertTrue(steepest > 8, "steepest " + steepest);
        }

        @Test
        void everyPointStandsWhollyBelowTheEyeLine() {
            RandomSource random = RandomSource.create(4L);
            double highest = Double.NEGATIVE_INFINITY;
            for (int i = 0; i < SAMPLES; i++) {
                ReapSlash slash = ReapSlash.launch(EYE, LEVEL_SOUTH, SHAPE, random);
                for (double age : new double[] {0, SHAPE.lifeTicks() / 2.0, SHAPE.lifeTicks()}) {
                    for (int step = 0; step <= 20; step++) {
                        for (double layer : new double[] {0, 1}) {
                            highest = Math.max(highest, slash.pointAt(age, step / 20.0, layer).y - EYE.y);
                            highest = Math.max(highest, slash.outerPointAt(age, step / 20.0, layer).y - EYE.y);
                        }
                    }
                }
            }
            assertTrue(highest < -0.15, "the highest point stands " + highest + " from the eye line");
        }

        @Test
        void itIsHollowTowardTheSlasherWithItsTipsABlockOutAndItsBulgeGrowingWithIt() {
            RandomSource random = RandomSource.create(6L);
            for (int i = 0; i < SAMPLES; i++) {
                ReapSlash slash = ReapSlash.launch(EYE, LEVEL_SOUTH, SHAPE, random);
                double width = slash.pointAt(0, 0, 1).distanceTo(slash.pointAt(0, 1, 1));
                assertEquals(1, ahead(slash.pointAt(0, 0, 1)), TOLERANCE, "near tip");
                assertEquals(1, ahead(slash.pointAt(0, 1, 1)), TOLERANCE, "far tip");
                assertEquals(1 + width * 0.55, ahead(slash.pointAt(0, 0.5, 1)), TOLERANCE, "bulge");
            }
        }

        @Test
        void itsSizeRunsFromUnderABlockToSixAcrossThickeningWithIt() {
            RandomSource random = RandomSource.create(5L);
            double narrowest = Double.MAX_VALUE;
            double widest = 0;
            for (int i = 0; i < SAMPLES; i++) {
                ReapSlash slash = ReapSlash.launch(EYE, LEVEL_SOUTH, SHAPE, random);
                double width = slash.pointAt(0, 0, 1).distanceTo(slash.pointAt(0, 1, 1));
                double thickness = slash.pointAt(0, 0.5, 1).distanceTo(slash.pointAt(0, 0.5, 0));
                assertTrue(width >= 0.8 - TOLERANCE && width <= 6 + TOLERANCE, "width " + width);
                assertEquals((width - 0.8) / 5.2, (thickness - 0.3) / 1.7, 0.001, "width " + width + ", thick " + thickness);
                narrowest = Math.min(narrowest, width);
                widest = Math.max(widest, width);
            }
            assertTrue(narrowest < 1.0 && widest > 5.8, "sizes " + narrowest + " to " + widest);
        }

        @Test
        void smallAndHugeSlashesComeUpAsOftenAsMiddlingOnes() {
            RandomSource random = RandomSource.create(8L);
            int small = 0;
            int middling = 0;
            int huge = 0;
            for (int i = 0; i < SAMPLES; i++) {
                ReapSlash slash = ReapSlash.launch(EYE, LEVEL_SOUTH, SHAPE, random);
                double share = (slash.width() - 0.8) / 5.2;
                if (share < 1 / 3.0) {
                    small++;
                } else if (share > 2 / 3.0) {
                    huge++;
                } else {
                    middling++;
                }
            }
            assertTrue(small >= middling && huge >= middling, "small " + small + ", middling " + middling + ", huge " + huge);
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
        void itTravelsOutwardAsItShrinksToNothing() {
            double half = SHAPE.lifeTicks() / 2.0;
            double travelled = ahead(slash.pointAt(SHAPE.lifeTicks(), 0.5, 1)) - ahead(slash.pointAt(0, 0.5, 1));
            assertEquals(1.5, travelled, TOLERANCE);
            assertEquals(slash.width() / 2, slash.pointAt(half, 0, 1).distanceTo(slash.pointAt(half, 1, 1)), TOLERANCE);
            assertEquals(slash.thickness() / 2, slash.pointAt(half, 0.5, 1).distanceTo(slash.pointAt(half, 0.5, 0)),
                    TOLERANCE);
            assertEquals(0, slash.pointAt(SHAPE.lifeTicks(), 0, 1).distanceTo(slash.pointAt(SHAPE.lifeTicks(), 1, 1)),
                    TOLERANCE);
        }

        @Test
        void aThinnerOuterBladeRunsOutAheadOfItFartherAndShrinksToo() {
            double life = SHAPE.lifeTicks();
            assertTrue(ahead(slash.outerPointAt(0, 0.5, 1)) > ahead(slash.pointAt(0, 0.5, 1)), "it starts beyond the body");
            double outerThickness = slash.outerPointAt(0, 0.5, 1).distanceTo(slash.outerPointAt(0, 0.5, 0));
            assertEquals(slash.thickness() * 0.25, outerThickness, TOLERANCE);
            double travelled = ahead(slash.outerPointAt(life, 0.5, 1)) - ahead(slash.outerPointAt(0, 0.5, 1));
            assertEquals(2.5, travelled, TOLERANCE);
            assertEquals(0, slash.outerPointAt(life, 0, 1).distanceTo(slash.outerPointAt(life, 1, 1)), TOLERANCE);
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
            assertTrue(box.contains(slash.outerPointAt(SHAPE.lifeTicks(), 0.5, 1)), "its outer blade once run out");
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
        void reapsSlashesRunUnderABlockToSixAcrossFlatAndUnderTheEye() {
            SlashStep step = (SlashStep) reap.behaviors().getFirst();
            assertEquals(0.8, step.narrowest().evaluate(null), TOLERANCE);
            assertEquals(6, step.widest().evaluate(null), TOLERANCE);
            assertEquals(0.3, step.thinnest().evaluate(null), TOLERANCE);
            assertEquals(2.0, step.thickest().evaluate(null), TOLERANCE);
            assertEquals(10, step.tilt().evaluate(null), TOLERANCE);
            assertEquals(0.6, step.dropLeast().evaluate(null), TOLERANCE);
            assertEquals(1.0, step.dropMost().evaluate(null), TOLERANCE);
        }

        @Test
        void reapSlashesEveryTwoOrThreeTicks() {
            SlashStep step = (SlashStep) reap.behaviors().getFirst();
            assertEquals(2, step.fewestTicks().evaluateInt(null));
            assertEquals(3, step.mostTicks().evaluateInt(null));
        }
    }
}
