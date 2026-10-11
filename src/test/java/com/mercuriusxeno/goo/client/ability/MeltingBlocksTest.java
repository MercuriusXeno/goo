package com.mercuriusxeno.goo.client.ability;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A worked block reads the share it has melted until the unmake goes quiet on
 * it (decision unmake-waves-dissolve-by-crucible-cost).
 */
class MeltingBlocksTest {

    private static final BlockPos BLOCK = new BlockPos(1, 2, 3);

    @Test
    void aWorkedBlockReadsItsShare() {
        MeltingBlocks melting = new MeltingBlocks();
        melting.record(BLOCK, 0.4f, 100);

        assertEquals(Optional.of(0.4f), melting.meltedAt(BLOCK, 101f));
        assertEquals(Map.of(BLOCK, 0.4f), melting.worked(101f));
    }

    @Test
    void aBlockTheUnmakeLeftReadsUnworked() {
        MeltingBlocks melting = new MeltingBlocks();
        melting.record(BLOCK, 0.4f, 100);

        assertTrue(melting.meltedAt(BLOCK, 101f + MeltingBlocks.STALE_TICKS).isEmpty());
        assertTrue(melting.worked(101f + MeltingBlocks.STALE_TICKS).isEmpty());
    }
}
