package com.mercuriusxeno.goo.ability.reap;

import com.mercuriusxeno.goo.ability.AbilityBadge;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.ability.DeliveryKind;
import com.mercuriusxeno.goo.ability.program.SlashStep;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers Reap's slashes: the heading each slash flies, inside the forward cone
 * and pitched between level and the dive that meets the ground at its reach,
 * the cells a slash's path crosses, and the shipped Reap taking free aim with
 * nothing thrown.
 * reap-is-crissaegrim-slashes-that-harvest-and-cut
 */
class ReapSlashesTest {

    private static final double CONE = 100;
    private static final double REACH = 6;
    private static final double LAUNCH_HEIGHT = 1.1;
    private static final int SAMPLES = 2000;
    private static final double TOLERANCE = 1e-4;

    @Nested
    class Heading {

        @Test
        void everySampledYawLiesInsideTheForwardCone() {
            RandomSource random = RandomSource.create(7L);
            float look = 170f;
            for (int i = 0; i < SAMPLES; i++) {
                ReapSlashes.Heading heading = ReapSlashes.sample(look, CONE, 10, random);
                assertTrue(Mth.degreesDifferenceAbs(look, heading.yaw()) <= CONE / 2 + TOLERANCE,
                        "yaw " + heading.yaw());
            }
        }

        @Test
        void everySampledPitchLiesBetweenLevelAndTheDownwardLimit() {
            RandomSource random = RandomSource.create(11L);
            double limit = ReapSlashes.dropLimit(LAUNCH_HEIGHT, REACH);
            for (int i = 0; i < SAMPLES; i++) {
                ReapSlashes.Heading heading = ReapSlashes.sample(0f, CONE, limit, random);
                assertTrue(heading.pitch() >= 0 && heading.pitch() <= limit + TOLERANCE, "pitch " + heading.pitch());
            }
        }

        @Test
        void theSamplesSpreadAcrossTheConeRatherThanFlyingTheLook() {
            RandomSource random = RandomSource.create(3L);
            double widest = 0;
            for (int i = 0; i < SAMPLES; i++) {
                widest = Math.max(widest, Mth.degreesDifferenceAbs(0f, ReapSlashes.sample(0f, CONE, 10, random).yaw()));
            }
            assertTrue(widest > CONE / 2 * 0.9, "widest " + widest);
        }

        @Test
        void theDownwardLimitMeetsTheGroundAtTheReach() {
            double limit = Math.toRadians(ReapSlashes.dropLimit(LAUNCH_HEIGHT, REACH));
            assertEquals(LAUNCH_HEIGHT, REACH * Math.tan(limit), TOLERANCE);
        }

        @Test
        void aPitchDownwardFliesBelowLevel() {
            Vec3 direction = new ReapSlashes.Heading(0f, 10f).direction();
            assertTrue(direction.y < 0, "y " + direction.y);
            assertEquals(1, direction.length(), TOLERANCE);
        }
    }

    @Nested
    class Path {

        private final Vec3 from = new Vec3(0.5, 1.5, 0.5);
        private final Vec3 to = new Vec3(6.5, 1.5, 0.5);

        @Test
        void theCellsOnThePathAreCrossed() {
            List<BlockPos> cells = ReapSlashes.cellsAlong(from, to);
            for (int x = 0; x <= 6; x++) {
                assertTrue(cells.contains(new BlockPos(x, 1, 0)), "cell " + x);
            }
        }

        @Test
        void aCellBesideThePathIsNotCrossed() {
            List<BlockPos> cells = ReapSlashes.cellsAlong(from, to);
            assertFalse(cells.contains(new BlockPos(3, 1, 1)));
            assertFalse(cells.contains(new BlockPos(3, 0, 0)));
        }

        @Test
        void eachCellIsCrossedOnceNearestFirst() {
            List<BlockPos> cells = ReapSlashes.cellsAlong(from, to);
            assertEquals(cells.stream().distinct().toList(), cells);
            assertEquals(new BlockPos(0, 1, 0), cells.getFirst());
            assertEquals(new BlockPos(6, 1, 0), cells.getLast());
        }
    }

    @Nested
    class ShippedReap {

        private final AbilityDefinition reap = AbilityJson.decode("leaf_reap");

        @Test
        void reapTakesFreeAimAndThrowsNothing() {
            assertEquals(AbilityBadge.FREE, reap.badge());
            assertEquals(DeliveryKind.SELF, reap.delivery().kind());
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
