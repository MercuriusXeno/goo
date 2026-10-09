package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.AbilityMath;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A black hole takes its sphere's cells core outward, every cell but the
 * marker's own, nearest the center first
 * (decision black-hole-leaves-a-compression-sphere).
 */
class HoardedBlocksTest {

    private static final BlockPos CENTER = new BlockPos(10, 64, -3);
    private static final int RADIUS = 5;

    @Test
    void theCellsRunCoreOutward() {
        List<BlockPos> cells = HoardedBlocks.coreOutward(CENTER, RADIUS);
        for (int index = 1; index < cells.size(); index++) {
            assertTrue(cells.get(index - 1).distSqr(CENTER) <= cells.get(index).distSqr(CENTER),
                    "cell " + index + " lies nearer the core than the one before it");
        }
        assertEquals(1, cells.getFirst().distSqr(CENTER));
    }

    @Test
    void theCellsAreTheWholeSphereButTheMarkersOwn() {
        int[] sphere = new int[1];
        AbilityMath.forEachInSphere(CENTER, RADIUS, cell -> sphere[0]++);
        List<BlockPos> cells = HoardedBlocks.coreOutward(CENTER, RADIUS);
        assertEquals(sphere[0] - 1, cells.size());
        assertFalse(cells.contains(CENTER));
    }
}
