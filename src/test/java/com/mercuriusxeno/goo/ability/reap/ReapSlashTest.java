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
 * frames show it: a thick crescent rolled its own amount up to 10 degrees
 * around the aim and turned its own heading up to 15 degrees off it, its
 * middle under the crosshair line, hollow toward the slasher and bulging
 * away, its tips about a block out and its bulge growing with its size, which
 * runs from small to huge with its thickness, as often small or huge as middling;
 * appearing thick, then thinning to a sliver as it travels outward, keeping
 * six tenths of its width; the box it cuts; and the shipped Reap channeled
 * with nothing thrown.
 * reap-is-crissaegrim-slashes-that-harvest-and-cut
 */
class ReapSlashTest {

    private static final Vec3 EYE = new Vec3(0.5, 1.62, 0.5);
    private static final Vec3 LEVEL_SOUTH = new Vec3(0, 0, 1);
    private static final ReapSlash.Shape SHAPE = new ReapSlash.Shape(0, 2, 5, 1.5, 4, 0.8, 0.3, 0.6,
            Math.toRadians(10), Math.toRadians(15), 2.5, 6);
    private static final int SAMPLES = 2000;
    private static final double TOLERANCE = 1e-6;

    private static ReapSlash slash(long seed) {
        return ReapSlash.launch(EYE, LEVEL_SOUTH, SHAPE, RandomSource.create(seed));
    }

    private static double ahead(ReapSlash slash, Vec3 point) {
        return point.subtract(EYE).dot(slash.forward());
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
                assertEquals(0, slash.span().dot(slash.forward()), TOLERANCE, "span " + slash.span());
                double offPlane = slash.pointAt(0, 0.5, 1).subtract(EYE).dot(normal);
                double under = Math.abs(offPlane);
                assertTrue(under >= 0.3 - TOLERANCE && under <= 0.6 + TOLERANCE, "under " + under);
                assertTrue(normal.scale(offPlane).y < 0, "it sits under the line of sight");
                lowest = Math.min(lowest, under);
                highest = Math.max(highest, under);
            }
            assertTrue(lowest < 0.35 && highest > 0.55, "drops " + lowest + " to " + highest);
        }

        @Test
        void everySlashRollsItsOwnAmountUpToTenDegreesEitherWayWhateverItsSize() {
            RandomSource random = RandomSource.create(3L);
            double steepestWide = 0;
            boolean rolledLeft = false;
            boolean rolledRight = false;
            for (int i = 0; i < SAMPLES; i++) {
                ReapSlash slash = ReapSlash.launch(EYE, LEVEL_SOUTH, SHAPE, random);
                double roll = Math.toDegrees(Math.asin(slash.span().y));
                assertTrue(Math.abs(roll) <= 10 + TOLERANCE, "roll " + roll);
                rolledLeft |= roll < -5;
                rolledRight |= roll > 5;
                if (slash.width() > 4.5) {
                    steepestWide = Math.max(steepestWide, Math.abs(roll));
                }
            }
            assertTrue(rolledLeft && rolledRight, "it rolls both ways");
            assertTrue(steepestWide > 9.5, "a wide slash rolls the full 10, steepest " + steepestWide);
        }

        @Test
        void everySlashTurnsItsOwnHeadingUpToFifteenDegreesOffTheAim() {
            RandomSource random = RandomSource.create(9L);
            double widest = 0;
            boolean turnedLeft = false;
            boolean turnedRight = false;
            for (int i = 0; i < SAMPLES; i++) {
                ReapSlash slash = ReapSlash.launch(EYE, LEVEL_SOUTH, SHAPE, random);
                double turn = Math.toDegrees(Math.atan2(slash.forward().x, slash.forward().z));
                assertEquals(0, slash.forward().y, TOLERANCE, "it stays level with a level aim");
                assertTrue(Math.abs(turn) <= 15 + TOLERANCE, "turn " + turn);
                turnedLeft |= turn < -10;
                turnedRight |= turn > 10;
                widest = Math.max(widest, Math.abs(turn));
            }
            assertTrue(turnedLeft && turnedRight, "it turns both ways");
            assertTrue(widest > 14.5, "widest turn " + widest);
        }

        @Test
        void itsMiddleStaysUnderTheCrosshairLine() {
            RandomSource random = RandomSource.create(4L);
            double highest = Double.NEGATIVE_INFINITY;
            for (int i = 0; i < SAMPLES; i++) {
                ReapSlash slash = ReapSlash.launch(EYE, LEVEL_SOUTH, SHAPE, random);
                for (double age : new double[] {0, SHAPE.lifeTicks() / 2.0, SHAPE.lifeTicks()}) {
                    for (double layer : new double[] {0, 1}) {
                        highest = Math.max(highest, slash.pointAt(age, 0.5, layer).y - EYE.y);
                    }
                }
            }
            assertTrue(highest < -0.25, "the middle stands " + highest + " from the crosshair line");
        }

        @Test
        void itsTipsStartAtTheChestItsInsideEdgeBowsOutEightTenthsAndItsOutsideEdgeAThicknessBeyond() {
            RandomSource random = RandomSource.create(6L);
            for (int i = 0; i < SAMPLES; i++) {
                ReapSlash slash = ReapSlash.launch(EYE, LEVEL_SOUTH, SHAPE, random);
                double width = slash.pointAt(0, 0, 1).distanceTo(slash.pointAt(0, 1, 1));
                assertEquals(0, ahead(slash, slash.pointAt(0, 0, 1)), TOLERANCE, "one tip at the chest");
                assertEquals(0, ahead(slash, slash.pointAt(0, 1, 1)), TOLERANCE, "the other tip at the chest");
                assertEquals(width * 0.8, ahead(slash, slash.pointAt(0, 0.5, 0)), TOLERANCE, "inside edge");
                assertEquals(width * 0.8 + slash.thickness(), ahead(slash, slash.pointAt(0, 0.5, 1)), TOLERANCE,
                        "outside edge");
                assertTrue(ahead(slash, slash.pointAt(0, 0.25, 0)) > width * 0.8 * 0.7, "the inside edge curves");
            }
        }

        @Test
        void itsSizeRunsFromTwoToFiveAcrossThickeningWithIt() {
            RandomSource random = RandomSource.create(5L);
            double narrowest = Double.MAX_VALUE;
            double widest = 0;
            for (int i = 0; i < SAMPLES; i++) {
                ReapSlash slash = ReapSlash.launch(EYE, LEVEL_SOUTH, SHAPE, random);
                double width = slash.pointAt(0, 0, 1).distanceTo(slash.pointAt(0, 1, 1));
                double thickness = slash.pointAt(0, 0.5, 1).distanceTo(slash.pointAt(0, 0.5, 0));
                assertTrue(width >= 2 - TOLERANCE && width <= 5 + TOLERANCE, "width " + width);
                assertEquals((width - 2) / 3, (thickness - 1.5) / 2.5, 0.001, "width " + width + ", thick " + thickness);
                narrowest = Math.min(narrowest, width);
                widest = Math.max(widest, width);
            }
            assertTrue(narrowest < 2.1 && widest > 4.9, "sizes " + narrowest + " to " + widest);
        }

        @Test
        void smallAndHugeSlashesComeUpAsOftenAsMiddlingOnes() {
            RandomSource random = RandomSource.create(8L);
            int small = 0;
            int middling = 0;
            int huge = 0;
            for (int i = 0; i < SAMPLES; i++) {
                ReapSlash slash = ReapSlash.launch(EYE, LEVEL_SOUTH, SHAPE, random);
                double share = (slash.width() - 2) / 3;
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
        void itTravelsTwoAndAHalfBlocksOutKeepingSixTenthsOfItsWidth() {
            double life = SHAPE.lifeTicks();
            double travelled = ahead(slash, slash.pointAt(life, 0.5, 0)) - ahead(slash, slash.pointAt(0, 0.5, 0));
            assertEquals(2.5, travelled, TOLERANCE);
            assertEquals(slash.width() * 0.8, slash.pointAt(life / 2, 0, 1).distanceTo(slash.pointAt(life / 2, 1, 1)),
                    TOLERANCE);
            assertEquals(slash.width() * 0.6, slash.pointAt(life, 0, 1).distanceTo(slash.pointAt(life, 1, 1)), TOLERANCE);
        }

        @Test
        void itAppearsThickThenThinsToASliver() {
            double life = SHAPE.lifeTicks();
            assertEquals(slash.thickness(), slash.pointAt(0, 0.5, 1).distanceTo(slash.pointAt(0, 0.5, 0)), TOLERANCE);
            assertEquals(slash.thickness() * 0.1, slash.pointAt(life, 0.5, 1).distanceTo(slash.pointAt(life, 0.5, 0)),
                    TOLERANCE);
        }
    }
    @Nested
    class FirstPersonTip {

        private final ReapSlash slash = slash(23L);
        private final ReapSlash tipped = slash.tippedUp(Math.toRadians(25));

        @Test
        void itsFarEdgeLiftsTwentyFiveDegreesAboutItsTips() {
            double turned = Math.toDegrees(Math.acos(Math.clamp(tipped.forward().dot(slash.forward()), -1, 1)));
            assertEquals(25, turned, TOLERANCE);
            assertTrue(tipped.pointAt(0, 0.5, 1).y > slash.pointAt(0, 0.5, 1).y, "the far edge lifts");
            assertEquals(0, tipped.forward().dot(slash.span()), TOLERANCE, "it turns about its tips' line");
        }

        @Test
        void itKeepsItsTipsItsSizeAndItsRoll() {
            assertEquals(0, tipped.pointAt(0, 0, 1).distanceTo(slash.pointAt(0, 0, 1)), TOLERANCE);
            assertEquals(0, tipped.pointAt(0, 1, 1).distanceTo(slash.pointAt(0, 1, 1)), TOLERANCE);
            assertEquals(slash.span(), tipped.span());
            assertEquals(slash.width(), tipped.width(), TOLERANCE);
            assertEquals(slash.thickness(), tipped.thickness(), TOLERANCE);
            assertEquals(slash.bulge(), tipped.bulge(), TOLERANCE);
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
        void reapsSlashesRunTwoToFiveAcrossAndOneAndAHalfToFourThickBowingOutEightTenths() {
            SlashStep step = (SlashStep) reap.behaviors().getFirst();
            assertEquals(2, step.narrowest().evaluate(null), TOLERANCE);
            assertEquals(5, step.widest().evaluate(null), TOLERANCE);
            assertEquals(1.5, step.thinnest().evaluate(null), TOLERANCE);
            assertEquals(4, step.thickest().evaluate(null), TOLERANCE);
            assertEquals(0.8, step.bulge().evaluate(null), TOLERANCE);
            assertEquals(0, step.near().evaluate(null), TOLERANCE);
            assertEquals(10, step.tilt().evaluate(null), TOLERANCE);
            assertEquals(15, step.fan().evaluate(null), TOLERANCE);
            assertEquals(0.3, step.dropLeast().evaluate(null), TOLERANCE);
            assertEquals(0.6, step.dropMost().evaluate(null), TOLERANCE);
        }

        @Test
        void reapSlashesEveryTwoOrThreeTicks() {
            SlashStep step = (SlashStep) reap.behaviors().getFirst();
            assertEquals(2, step.fewestTicks().evaluateInt(null));
            assertEquals(3, step.mostTicks().evaluateInt(null));
        }

        @Test
        void reapThrowsOneOrTwoSlashesASwingEachTravellingTwoAndAHalfBlocks() {
            SlashStep step = (SlashStep) reap.behaviors().getFirst();
            assertEquals(1, step.fewestSlashes().evaluateInt(null));
            assertEquals(2, step.mostSlashes().evaluateInt(null));
            assertEquals(2.5, step.drift().evaluate(null), TOLERANCE);
            assertEquals(6, step.lifeTicks().evaluateInt(null));
        }
    }
}
