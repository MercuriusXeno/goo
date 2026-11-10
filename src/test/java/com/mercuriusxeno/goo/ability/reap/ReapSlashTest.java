package com.mercuriusxeno.goo.ability.reap;

import com.mercuriusxeno.goo.ability.AbilityBadge;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.ability.DeliveryKind;
import com.mercuriusxeno.goo.ability.HeldRoute;
import com.mercuriusxeno.goo.ability.program.SlashStep;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers Reap's slash, the Crissaegrim's from Symphony of the Night: a fixed
 * crescent standing about 1.5 blocks ahead of the eye, facing the slasher,
 * wholly below the eye line; a bright band sweeping its arc from one tip to
 * the other and the arc wiping away behind it, the crescent itself never
 * moving; the three forms it takes in turn; the box it cuts; and the shipped
 * Reap channeled with nothing thrown.
 * reap-is-crissaegrim-slashes-that-harvest-and-cut
 */
class ReapSlashTest {

    private static final Vec3 EYE = new Vec3(0.5, 1.62, 0.5);
    private static final Vec3 LEVEL_SOUTH = new Vec3(0, 0, 1);
    private static final ReapSlash.Shape SHAPE = new ReapSlash.Shape(1.5, Math.toRadians(100), 0.45, 0.75,
            Math.toRadians(25), 4);
    private static final int STEPS = 40;
    private static final double TOLERANCE = 1e-6;

    private static ReapSlash slash(SlashForm form) {
        return ReapSlash.launch(EYE, LEVEL_SOUTH, form, SHAPE);
    }

    @Nested
    class Crescent {

        @Test
        void itsMiddleStandsAboutABlockAndAHalfAheadOfTheEye() {
            for (SlashForm form : SlashForm.values()) {
                Vec3 middle = slash(form).pointAt(0.5, 1);
                double ahead = middle.subtract(EYE).dot(LEVEL_SOUTH);
                assertEquals(1.5, ahead, 0.1, form + " ahead " + ahead);
            }
        }

        @Test
        void itFacesTheSlasherItsArcCurvingAroundTheEye() {
            ReapSlash flat = slash(SlashForm.FLAT);
            for (int step = 0; step <= STEPS; step++) {
                double t = (double) step / STEPS;
                Vec3 point = flat.pointAt(t, 1);
                Vec3 flatFromEye = new Vec3(point.x - EYE.x, 0, point.z - EYE.z);
                assertEquals(1.5, flatFromEye.length(), 0.01, "outer edge at " + t);
            }
            assertTrue(flat.pointAt(0, 1).subtract(EYE).dot(LEVEL_SOUTH) < flat.pointAt(0.5, 1).subtract(EYE)
                    .dot(LEVEL_SOUTH), "the tips curve back toward the slasher");
        }

        @Test
        void itSpansAboutTwoAndAThirdBlocksAcross() {
            ReapSlash flat = slash(SlashForm.FLAT);
            assertEquals(2.3, flat.pointAt(0, 1).distanceTo(flat.pointAt(1, 1)), 0.1);
        }

        @Test
        void itIsThickestMidArcAndItsTipsMeet() {
            ReapSlash flat = slash(SlashForm.FLAT);
            assertEquals(0, flat.pointAt(0, 1).distanceTo(flat.pointAt(0, 0)), TOLERANCE);
            assertEquals(0, flat.pointAt(1, 1).distanceTo(flat.pointAt(1, 0)), TOLERANCE);
            double middle = flat.pointAt(0.5, 1).distanceTo(flat.pointAt(0.5, 0));
            assertEquals(0.45, middle, TOLERANCE);
            assertTrue(flat.pointAt(0.25, 1).distanceTo(flat.pointAt(0.25, 0)) < middle);
        }

        @Test
        void everyFormStandsWhollyBelowTheEyeLine() {
            for (SlashForm form : SlashForm.values()) {
                ReapSlash formed = slash(form);
                for (int step = 0; step <= STEPS; step++) {
                    for (double layer : new double[] {0, 0.5, 1}) {
                        double above = formed.pointAt((double) step / STEPS, layer).y - EYE.y;
                        assertTrue(above < -0.15, form + " stands " + above + " from the eye line");
                    }
                }
            }
        }

        @Test
        void theDiagonalsSlopeOppositeWaysAndTheFlatOneLiesLevel() {
            double down = slash(SlashForm.DOWN_DIAGONAL).pointAt(1, 1).y - slash(SlashForm.DOWN_DIAGONAL).pointAt(0, 1).y;
            double up = slash(SlashForm.UP_DIAGONAL).pointAt(1, 1).y - slash(SlashForm.UP_DIAGONAL).pointAt(0, 1).y;
            double flat = slash(SlashForm.FLAT).pointAt(1, 1).y - slash(SlashForm.FLAT).pointAt(0, 1).y;
            assertTrue(down < -0.5, "down " + down);
            assertTrue(up > 0.5, "up " + up);
            assertEquals(0, flat, TOLERANCE);
        }
    }

    @Nested
    class Sweep {

        private final ReapSlash flat = slash(SlashForm.FLAT);

        @Test
        void aBrightBandSweepsFromOneTipToTheOther() {
            assertEquals(0, flat.headAt(0), TOLERANCE);
            assertTrue(flat.headAt(1) > 0 && flat.headAt(1) < 1, "head " + flat.headAt(1));
            assertEquals(1, flat.headAt(SHAPE.lifeTicks()), TOLERANCE);
        }

        @Test
        void theArcWipesAwayBehindItFromTheFirstTip() {
            assertEquals(0, flat.tailAt(1), TOLERANCE);
            double late = SHAPE.lifeTicks() - 1;
            assertTrue(flat.tailAt(late) > 0 && flat.tailAt(late) < flat.headAt(late), "tail " + flat.tailAt(late));
            assertEquals(1, flat.tailAt(SHAPE.lifeTicks()), TOLERANCE);
        }

        @Test
        void theCrescentNeverMoves() {
            Vec3 first = flat.pointAt(0.5, 1);
            assertEquals(first, ReapSlash.launch(EYE, LEVEL_SOUTH, SlashForm.FLAT, SHAPE).pointAt(0.5, 1));
            assertTrue(flat.lifeTicks() == SHAPE.lifeTicks());
        }
    }

    @Nested
    class Box {

        @Test
        void itCutsInFrontOfTheSlasherBelowTheEyeLine() {
            AABB box = slash(SlashForm.FLAT).box(1.4, 1.2);
            assertEquals(1.4, box.getXsize(), TOLERANCE);
            assertEquals(1.2, box.getYsize(), TOLERANCE);
            assertTrue(box.maxY < EYE.y, "top " + (box.maxY - EYE.y));
            assertTrue(box.getCenter().subtract(EYE).dot(LEVEL_SOUTH) > 0.5, "the box stands ahead");
            assertTrue(box.contains(EYE.add(0, -1, 1.3)), "a body ahead at chest height is inside");
            assertFalse(box.contains(EYE.add(0, -1, -1)), "a body behind is outside");
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
