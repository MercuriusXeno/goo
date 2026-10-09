package com.mercuriusxeno.goo.ability.pulse;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A relay link crosses every cell between the two relays' centers, the
 * relays themselves aside (decision relay-prism-carries-the-signal-through-air).
 */
class RelayNetworkTest {

    @Test
    void aStraightLinkCrossesEachCellBetween() {
        Set<BlockPos> cells = RelayNetwork.cellsBetween(new BlockPos(0, 0, 0), new BlockPos(12, 0, 0));
        assertEquals(11, cells.size());
        for (int x = 1; x <= 11; x++) {
            assertTrue(cells.contains(new BlockPos(x, 0, 0)), "x=" + x);
        }
    }

    @Test
    void theRelaysThemselvesAreNotBetween() {
        Set<BlockPos> cells = RelayNetwork.cellsBetween(new BlockPos(0, 0, 0), new BlockPos(5, 3, 2));
        assertFalse(cells.contains(new BlockPos(0, 0, 0)));
        assertFalse(cells.contains(new BlockPos(5, 3, 2)));
    }

    @Test
    void neighboringRelaysHaveNothingBetween() {
        assertTrue(RelayNetwork.cellsBetween(new BlockPos(0, 0, 0), new BlockPos(1, 0, 0)).isEmpty());
    }

    @Test
    void aDiagonalLinkCrossesCellsOnTheDiagonal() {
        Set<BlockPos> cells = RelayNetwork.cellsBetween(new BlockPos(0, 0, 0), new BlockPos(4, 4, 4));
        assertTrue(cells.contains(new BlockPos(2, 2, 2)));
    }
}
