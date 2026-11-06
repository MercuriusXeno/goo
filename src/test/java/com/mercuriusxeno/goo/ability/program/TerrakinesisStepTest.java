package com.mercuriusxeno.goo.ability.program;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Terrakinesis gathers its hunk from the struck block through joined
 * liftable blocks within the radius, and a left click hucks it
 * (decision rock-grabs-hunks-of-rock-goo-blocks-as-a-huckable-entity).
 */
class TerrakinesisStepTest {

    private static final BlockPos STRUCK = new BlockPos(0, 64, 0);
    private static final double RADIUS = 1.5;
    private static final TerrakinesisStep TERRAKINESIS = new TerrakinesisStep(6, RADIUS, 2.5, 3.5, 1.5);

    @Nested
    class HunkCells {

        @Test
        void gathersEveryJoinedLiftableBlockWithinTheRadius() {
            List<BlockPos> cells = TerrakinesisStep.hunkCells(STRUCK, RADIUS, pos -> true);
            assertEquals(STRUCK, cells.getFirst());
            // the 3x3x3 around the struck block, its 8 corners lying past 1.5
            assertEquals(19, cells.size());
            assertTrue(cells.stream().allMatch(cell -> cell.distSqr(STRUCK) <= RADIUS * RADIUS));
        }

        @Test
        void aBlockJoinedOnlyThroughAnUnliftableOneStays() {
            BlockPos gap = STRUCK.above();
            BlockPos beyond = gap.east();
            Set<BlockPos> liftable = Set.of(STRUCK, beyond);
            assertEquals(List.of(STRUCK), TerrakinesisStep.hunkCells(STRUCK, RADIUS, liftable::contains));
        }

        @Test
        void aWideRadiusStopsAtTheMostBlocks() {
            assertEquals(TerrakinesisStep.MOST_BLOCKS, TerrakinesisStep.hunkCells(STRUCK, 10, pos -> true).size());
        }
    }

    @Test
    void aLeftClickHucksTheHeldHunk() {
        assertTrue(GrabStep.throwsOnAttack(true, List.of(TERRAKINESIS)));
    }
}
