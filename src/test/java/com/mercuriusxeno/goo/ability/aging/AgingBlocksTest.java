package com.mercuriusxeno.goo.ability.aging;

import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A chunk's aging marks read back from their saved form as they were saved, so an aging survives the chunk
 * unloading and loading again (decision old-blob-ages-valuables-slowly).
 */
class AgingBlocksTest {

    @Test
    void aChunksAgingMarksReadBackAsTheyWereSaved() {
        AgingBlocks marks = new AgingBlocks(List.of(
                new AgingBlocks.Aging(new BlockPos(3, 64, -2), Identifier.withDefaultNamespace("coal_block"), 1200),
                new AgingBlocks.Aging(new BlockPos(4, 64, -2), Identifier.withDefaultNamespace("gold_block"), 7)));

        AgingBlocks loaded = AgingBlocks.CODEC.codec().parse(JsonOps.INSTANCE,
                AgingBlocks.CODEC.codec().encodeStart(JsonOps.INSTANCE, marks).getOrThrow()).getOrThrow();

        assertEquals(marks, loaded);
    }
}
