package com.mercuriusxeno.goo.block.ability;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
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

    /**
     * The giga drill drains a network from its edges inward, the bored cell last.
     * boring-through-rock-goo-gives-a-giga-drill-that-drains-it
     */
    @Nested
    class DrainOrder {

        @Test
        void aLineBoredAtOneEndDrainsFromTheFarEndToTheBoredCell() {
            List<BlockPos> line = List.of(START, START.east(), START.east(2));
            Set<BlockPos> network = new HashSet<>(line);
            List<BlockPos> drained = new ArrayList<>();

            for (int spent = 0; spent < line.size(); spent++) {
                BlockPos next = MortarCellBlock.nextToDrain(network, START).orElseThrow();
                drained.add(next);
                network.remove(next);
            }

            assertEquals(List.of(START.east(2), START.east(), START), drained);
        }

        @Test
        void aSpentNetworkHasNothingToDrain() {
            assertEquals(Optional.empty(), MortarCellBlock.nextToDrain(Set.of(), START));
        }
    }
}
