package com.mercuriusxeno.goo.client.overlay;

import com.mercuriusxeno.goo.ability.SpireFootprint;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Spire's preview ripples over exactly the footprint's ground cells and
 * ghosts exactly the cells the rise would fill above them
 * (decision spire-previews-as-a-surface-ripple-and-ghost-stone).
 */
class SpirePreviewTest {

    private static final int RISE = 4;
    /** A 2x3 footprint on ground at y 64. */
    private static final SpireFootprint FOOTPRINT =
            new SpireFootprint(new BlockPos(10, 64, 20), new BlockPos(11, 64, 22), RISE);

    @Test
    void theRippleCoversEachGroundCellOnce() {
        List<BlockPos> ripple = SpirePreview.rippleCells(FOOTPRINT);
        assertEquals(6, ripple.size());
        assertEquals(Set.copyOf(FOOTPRINT.groundCells()), Set.copyOf(ripple));
    }

    @Test
    void theGhostFillsTheRiseAboveEveryGroundCell() {
        List<BlockPos> ghost = SpirePreview.ghostCells(FOOTPRINT);
        assertEquals(24, ghost.size());
        assertEquals(24, new HashSet<>(ghost).size());
        for (BlockPos cell : ghost) {
            assertTrue(cell.getY() >= 65 && cell.getY() <= 64 + RISE, cell + " lies outside the rise");
            assertTrue(FOOTPRINT.groundCells().contains(new BlockPos(cell.getX(), 64, cell.getZ())),
                    cell + " stands over no ground cell");
        }
    }
}
