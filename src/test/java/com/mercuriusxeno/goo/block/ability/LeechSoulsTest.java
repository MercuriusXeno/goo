package com.mercuriusxeno.goo.block.ability;

import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.ability.program.LeechGooStep;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the souls Leech goo pops: a cell pops one when the draw falls under
 * the rate hex_leech.json ships, and each rises from the goo's surface at
 * the height the cell's layers reach, inside the cell's column.
 * soul-particles-pop-from-leech-goo-now-and-then
 */
class LeechSoulsTest {

    private static final double RATE = LeechGooStep.in(AbilityJson.decode("hex_leech").behaviors())
            .orElseThrow().soulPopChance();

    @Nested
    class Pops {

        @Test
        void aDrawUnderTheRatePopsASoul() {
            assertTrue(LeechSouls.pops(RATE / 2, RATE));
        }

        @Test
        void aDrawOverTheRatePopsNone() {
            assertFalse(LeechSouls.pops((1 + RATE) / 2, RATE));
        }
    }

    @Nested
    class RisePoint {

        private static final BlockPos CELL = new BlockPos(4, 70, -3);
        private static final double ACROSS_X = 0.25;
        private static final double ACROSS_Z = 0.75;

        @Test
        void aOneLayerCellRisesFromItsThinSurfaceInsideItsColumn() {
            Vec3 rise = LeechSouls.risePoint(CELL, 1, ACROSS_X, ACROSS_Z);

            assertEquals(new Vec3(CELL.getX() + ACROSS_X, CELL.getY() + 1.0 / VolumeCellBlock.MAX_LAYERS,
                    CELL.getZ() + ACROSS_Z), rise);
        }

        @Test
        void aFullCellRisesFromItsTopHigherThanAOneLayerCell() {
            Vec3 full = LeechSouls.risePoint(CELL, VolumeCellBlock.MAX_LAYERS, ACROSS_X, ACROSS_Z);
            Vec3 thin = LeechSouls.risePoint(CELL, 1, ACROSS_X, ACROSS_Z);

            assertEquals(new Vec3(CELL.getX() + ACROSS_X, CELL.getY() + 1.0, CELL.getZ() + ACROSS_Z), full);
            assertTrue(full.y > thin.y, full + " over " + thin);
        }
    }
}
