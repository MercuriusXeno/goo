package com.mercuriusxeno.goo.block.ability;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Covers the network a Mortar cell reads for the combos that drain, carry
 * and lift it: the cells joined to it through their faces, nearest first, up
 * to a limit.
 * mortar-is-rocks-residual-a-petrification-and-hardening-goo
 */
class MortarCellBlockTest {

    private static final BlockPos START = new BlockPos(0, 64, 0);
    private static final int NO_LIMIT = 64;

    @Test
    void aNetworkHoldsTheCellsJoinedThroughFacesNearestFirst() {
        Set<BlockPos> mortar = Set.of(START, START.east(), START.east(2), START.east().above());

        assertEquals(List.of(START, START.east(), START.east().above(), START.east(2)),
                List.copyOf(MortarCellBlock.networkFrom(START, mortar::contains, NO_LIMIT)));
    }

    @Test
    void aCellTouchingOnlyAtAnEdgeIsOutsideTheNetwork() {
        Set<BlockPos> mortar = Set.of(START, START.east().north());

        assertEquals(Set.of(START), MortarCellBlock.networkFrom(START, mortar::contains, NO_LIMIT));
    }

    @Test
    void aNetworkStopsAtItsLimit() {
        Set<BlockPos> mortar = Set.of(START, START.east(), START.east(2), START.east(3));

        assertEquals(List.of(START, START.east()),
                List.copyOf(MortarCellBlock.networkFrom(START, mortar::contains, 2)));
    }

    @Test
    void aBlockHoldingNoMortarHasNoNetwork() {
        assertEquals(Set.of(), MortarCellBlock.networkFrom(START, pos -> false, NO_LIMIT));
    }
}
