package com.mercuriusxeno.goo.ability;

import com.mercuriusxeno.goo.ability.program.BuildStep;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Build's bounds hold a footprint to its corner's level within the span and the rise, the shipped ones at sixteen.
 * decision build-dimensions-reach-sixteen-by-sixteen-by-sixteen
 * decision spire-rips-walls-and-platforms
 */
class BuildBoundsTest {

    private static final BlockPos CORNER = new BlockPos(0, 64, 0);

    @Nested
    class Shipped {

        private final BuildBounds shipped = BuildStep.boundsOf(AbilityJson.decode("rock_build").behaviors());

        @Test
        void aDragAndARisePastSixteenReadSixteen() {
            BuildFootprint footprint = shipped.cap(CORNER, new BlockPos(19, 64, 29), 25);

            assertEquals(new BlockPos(15, 64, 15), footprint.opposite());
            assertEquals(16, footprint.rise());
        }

        @Test
        void aSixteenBySixteenDragRisingSixteenStandsUnchanged() {
            BuildFootprint footprint = shipped.cap(CORNER, new BlockPos(-15, 64, 15), 16);

            assertEquals(new BuildFootprint(CORNER, new BlockPos(-15, 64, 15), 16), footprint);
            assertTrue(shipped.admits(footprint));
        }
    }

    @Nested
    class Capping {

        private final BuildBounds bounds = new BuildBounds(4, 3);

        @Test
        void aDragPastTheSpanIsPulledBackOntoTheCornersLevel() {
            BuildFootprint footprint = bounds.cap(CORNER, new BlockPos(20, 70, -20), 2);

            assertEquals(new BlockPos(3, 64, -3), footprint.opposite());
            assertTrue(bounds.admits(footprint));
        }

        @Test
        void aRiseAskedPastTheBoundsIsHeldWithinThem() {
            assertEquals(3, bounds.cap(CORNER, CORNER, 99).rise());
            assertEquals(BuildBounds.MIN_RISE, bounds.cap(CORNER, CORNER, 0).rise());
        }

        @Test
        void aSubmitPastAnyBoundIsRefused() {
            assertFalse(bounds.admits(new BuildFootprint(CORNER, new BlockPos(4, 64, 0), 1)));
            assertFalse(bounds.admits(new BuildFootprint(CORNER, new BlockPos(0, 64, 4), 1)));
            assertFalse(bounds.admits(new BuildFootprint(CORNER, new BlockPos(1, 65, 1), 1)));
            assertFalse(bounds.admits(new BuildFootprint(CORNER, CORNER, 4)));
            assertFalse(bounds.admits(new BuildFootprint(CORNER, CORNER, 0)));
        }
    }
}
