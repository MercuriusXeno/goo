package com.mercuriusxeno.goo.block.ability;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A detonating Nitro cell sets off every other Nitro cell within its
 * blast's reach, nearest first, and none past it.
 * nitro-is-unstables-goo-that-explodes-in-a-chain-by-its-quantity
 */
class NitroChainTest {

    private static final BlockPos DETONATING = new BlockPos(10, 64, 10);
    private static final BlockPos TWO_EAST = DETONATING.east(2);
    private static final BlockPos ONE_ABOVE = DETONATING.above();
    private static final BlockPos FIVE_WEST = DETONATING.west(5);

    @Test
    void theCellsInReachGoOffNearestFirstAndTheDetonatingCellAndTheFarOnesDoNot() {
        List<BlockPos> next = NitroChain.setOffNext(DETONATING, 3.0,
                List.of(FIVE_WEST, TWO_EAST, DETONATING, ONE_ABOVE));

        assertEquals(List.of(ONE_ABOVE, TWO_EAST), next);
    }

    @Test
    void aCellExactlyAtTheReachGoesOff() {
        assertEquals(List.of(TWO_EAST), NitroChain.setOffNext(DETONATING, 2.0, List.of(TWO_EAST)));
    }
}
