package com.mercuriusxeno.goo.client.ability;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A melting block liquefies while it stands, its goo collapses once it is
 * gone, and a block the unmake left is forgotten
 * (decision unmake-waves-dissolve-by-crucible-cost).
 */
class MeltingBlocksTest {

    private static final BlockPos BLOCK = new BlockPos(1, 2, 3);
    private static final float DELTA = 1e-5f;

    @Test
    void aStandingBlockShowsTheShareLiquefied() {
        MeltingBlocks melting = new MeltingBlocks();
        melting.record(BLOCK, 0.4f, 100);

        assertEquals(List.of(new MeltingBlocks.Melt(BLOCK, 0.4f, 0f)), melting.melts(101f, pos -> false));
    }

    @Test
    void aGoneBlocksGooCollapsesThenIsForgotten() {
        MeltingBlocks melting = new MeltingBlocks();
        melting.record(BLOCK, 0.9f, 100);

        melting.melts(101f, pos -> true);
        MeltingBlocks.Melt halfway = melting.melts(101f + MeltingBlocks.COLLAPSE_TICKS / 2f, pos -> true).getFirst();

        assertEquals(1f, halfway.liquefied(), DELTA);
        assertEquals(0.5f, halfway.collapse(), DELTA);
        assertTrue(melting.melts(101f + MeltingBlocks.COLLAPSE_TICKS, pos -> true).isEmpty());
    }

    @Test
    void aStandingBlockTheUnmakeLeftIsForgotten() {
        MeltingBlocks melting = new MeltingBlocks();
        melting.record(BLOCK, 0.4f, 100);

        assertTrue(melting.melts(101f + MeltingBlocks.STALE_TICKS, pos -> false).isEmpty());
    }
}
