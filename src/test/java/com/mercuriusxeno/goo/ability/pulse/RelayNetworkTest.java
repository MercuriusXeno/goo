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
    void relaysAlongEachAxisWithinRangeLink() {
        assertTrue(RelayNetwork.inLinkReach(BlockPos.ZERO, new BlockPos(RelayNetwork.RANGE, 0, 0)));
        assertTrue(RelayNetwork.inLinkReach(BlockPos.ZERO, new BlockPos(0, -RelayNetwork.RANGE, 0)));
        assertTrue(RelayNetwork.inLinkReach(BlockPos.ZERO, new BlockPos(0, 0, 3)));
    }

    @Test
    void relaysPastTheRangeDoNotLink() {
        assertFalse(RelayNetwork.inLinkReach(BlockPos.ZERO, new BlockPos(RelayNetwork.RANGE + 1, 0, 0)));
    }

    /** Relays link orthogonally only: one step off the axis breaks the link. */
    @Test
    void diagonalRelaysDoNotLink() {
        assertFalse(RelayNetwork.inLinkReach(BlockPos.ZERO, new BlockPos(5, 1, 0)));
        assertFalse(RelayNetwork.inLinkReach(BlockPos.ZERO, new BlockPos(0, 2, 2)));
        assertFalse(RelayNetwork.inLinkReach(BlockPos.ZERO, new BlockPos(1, 1, 1)));
    }

    @Test
    void aRelayDoesNotLinkToItself() {
        assertFalse(RelayNetwork.inLinkReach(BlockPos.ZERO, BlockPos.ZERO));
    }
}
