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
 * Covers Reap's slash, the Crissaegrim's from Symphony of the Night: a fixed
 * crescent standing upright across the view, facing the slasher, about 2.5
 * blocks out along the look and centred just under the aim; each slash drawn
 * at its own tilt, sweep, sideways place and size, its width and thickness
 * together; a bright band sweeping its arc from one tip to the other and the
 * arc wiping away behind it, the crescent never moving; the box it cuts; and
 * the shipped Reap channeled with nothing thrown.
 * reap-is-crissaegrim-slashes-that-harvest-and-cut
 */
class ReapSlashTest {

    private static final Vec3 EYE = new Vec3(0.5, 1.62, 0.5);
    private static final Vec3 LEVEL_SOUTH = new Vec3(0, 0, 1);
    private static final ReapSlash.Shape SHAPE = new ReapSlash.Shape(2.5, 2.5, 3.5, 0.6, 1.0, 0.6, 0.3,
            Math.toRadians(35), 4);
    private static final int SAMPLES = 2000;
    private static final double TOLERANCE = 1e-6;

    private static ReapSlash slash(long seed) {
        return ReapSlash.launch(EYE, LEVEL_SOUTH, SHAPE, RandomSource.create(seed));
    }

    /** The middle of a slash's band, halfway between its edges at mid-arc. */
    private static Vec3 middleOf(ReapSlash slash) {
        return slash.pointAt(0.5, 1).add(slash.pointAt(0.5, 0)).scale(0.5);
    }

    @Nested
    class Crescent {

        @Test
        void itStandsUprightAcrossTheViewFacingTheSlasher() {
            for (long seed = 0; seed < 50; seed++) {
                ReapSlash slash = slash(seed);
                assertEquals(0, slash.across().dot(LEVEL_SOUTH), TOLERANCE, "across " + slash.across());
                assertEquals(0, slash.rise().dot(LEVEL_SOUTH), TOLERANCE, "rise " + slash.rise());
            }
        }

        @Test
        void itStandsAboutTwoAndAHalfBlocksOutCentredJustUnderTheAim() {
            RandomSource random = RandomSource.create(3L);
            for (int i = 0; i < SAMPLES; i++) {
                Vec3 fromEye = middleOf(ReapSlash.launch(EYE, LEVEL_SOUTH, SHAPE, random)).subtract(EYE);
                assertEquals(2.5, fromEye.z, 0.01, "ahead " + fromEye.z);
                assertTrue(Math.abs(fromEye.x) <= 0.6 + TOLERANCE, "sideways " + fromEye.x);
                assertEquals(-0.3, fromEye.y, 0.01, "under the aim " + fromEye.y);
            }
        }

        @Test
        void itsSizeRunsFromTwoAndAHalfToThreeAndAHalfAcrossThickeningWithIt() {
            RandomSource random = RandomSource.create(5L);
            double narrowest = Double.MAX_VALUE;
            double widest = 0;
            for (int i = 0; i < SAMPLES; i++) {
                ReapSlash slash = ReapSlash.launch(EYE, LEVEL_SOUTH, SHAPE, random);
                double width = slash.pointAt(0, 1).distanceTo(slash.pointAt(1, 1));
                double thickness = slash.pointAt(0.5, 1).distanceTo(slash.pointAt(0.5, 0));
                assertTrue(width >= 2.5 - 0.01 && width <= 3.5 + 0.01, "width " + width);
                assertEquals((width - 2.5) / 1.0, (thickness - 0.6) / 0.4, 0.02, "width " + width + ", thick " + thickness);
                narrowest = Math.min(narrowest, width);
                widest = Math.max(widest, width);
            }
            assertTrue(narrowest < 2.6 && widest > 3.4, "sizes " + narrowest + " to " + widest);
        }

        @Test
        void eachSlashTiltsItsOwnWayWithinThirtyFiveDegrees() {
            RandomSource random = RandomSource.create(7L);
            double steepestDown = 0;
            double steepestUp = 0;
            for (int i = 0; i < SAMPLES; i++) {
                ReapSlash slash = ReapSlash.launch(EYE, LEVEL_SOUTH, SHAPE, random);
                Vec3 right = new Vec3(-1, 0, 0);
                double tilt = Math.toDegrees(Math.atan2(slash.across().y, slash.across().dot(right)));
                assertTrue(Math.abs(tilt) <= 35 + TOLERANCE, "tilt " + tilt);
                steepestDown = Math.min(steepestDown, tilt);
                steepestUp = Math.max(steepestUp, tilt);
            }
            assertTrue(steepestDown < -30 && steepestUp > 30, "tilts " + steepestDown + " to " + steepestUp);
        }

        @Test
        void slashesSweepFromEitherTip() {
            RandomSource random = RandomSource.create(9L);
            int fromLeft = 0;
            for (int i = 0; i < SAMPLES; i++) {
                fromLeft += ReapSlash.launch(EYE, LEVEL_SOUTH, SHAPE, random).sweep() > 0 ? 1 : 0;
            }
            assertTrue(fromLeft > SAMPLES / 4 && fromLeft < SAMPLES * 3 / 4, "from the left " + fromLeft);
        }

        @Test
        void itIsThickestMidArcAndItsTipsMeet() {
            ReapSlash slash = slash(11L);
            assertEquals(0, slash.pointAt(0, 1).distanceTo(slash.pointAt(0, 0)), TOLERANCE);
            assertEquals(0, slash.pointAt(1, 1).distanceTo(slash.pointAt(1, 0)), TOLERANCE);
            double middle = slash.pointAt(0.5, 1).distanceTo(slash.pointAt(0.5, 0));
            assertTrue(slash.pointAt(0.25, 1).distanceTo(slash.pointAt(0.25, 0)) < middle);
        }

        @Test
        void itBowsAwayFromItsChord() {
            ReapSlash slash = slash(13L);
            Vec3 chordMiddle = slash.pointAt(0, 1).add(slash.pointAt(1, 1)).scale(0.5);
            assertTrue(slash.pointAt(0.5, 1).subtract(chordMiddle).dot(slash.rise()) > 0.3, "the arc bows");
        }
    }

    @Nested
    class Sweep {

        private final ReapSlash slash = slash(17L);

        @Test
        void aBrightBandSweepsFromOneTipToTheOther() {
            assertEquals(0, slash.headAt(0), TOLERANCE);
            assertTrue(slash.headAt(1) > 0 && slash.headAt(1) < 1, "head " + slash.headAt(1));
            assertEquals(1, slash.headAt(SHAPE.lifeTicks()), TOLERANCE);
        }

        @Test
        void theArcWipesAwayBehindItFromTheFirstTip() {
            assertEquals(0, slash.tailAt(1), TOLERANCE);
            double late = SHAPE.lifeTicks() - 1;
            assertTrue(slash.tailAt(late) > 0 && slash.tailAt(late) < slash.headAt(late), "tail " + slash.tailAt(late));
            assertEquals(1, slash.tailAt(SHAPE.lifeTicks()), TOLERANCE);
        }

        @Test
        void theCrescentNeverMoves() {
            assertEquals(slash.pointAt(0.5, 1), slash(17L).pointAt(0.5, 1));
            assertEquals(SHAPE.lifeTicks(), slash.lifeTicks());
        }
    }

    @Nested
    class Box {

        @Test
        void itReachesFromABlockToThreeAndAHalfAhead() {
            AABB box = slash(19L).box(1, 3.5, 1.5);
            assertTrue(box.contains(EYE.add(0, -0.3, 1.2)), "near the slasher");
            assertTrue(box.contains(EYE.add(0, -0.3, 3.3)), "out at its reach");
            assertTrue(box.contains(EYE.add(0, -0.9, 2.5)), "a body's middle under the slash");
            assertFalse(box.contains(EYE.add(0, -0.3, 0.5)), "too close");
            assertFalse(box.contains(EYE.add(0, -0.3, -1)), "behind");
            assertFalse(box.contains(EYE.add(0, -0.3, 4)), "past its reach");
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
