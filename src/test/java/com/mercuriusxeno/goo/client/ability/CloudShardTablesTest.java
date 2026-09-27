package com.mercuriusxeno.goo.client.ability;

import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * A cloud keeps the layout drawn when the client first saw it while it
 * stands, and draws a new one once it stops rendering
 * (decision cloud-seed-per-client-ephemeral).
 */
class CloudShardTablesTest {

    private static final BlockPos MARKER = new BlockPos(10, 64, 10);
    private static final BlockPos NEIGHBOR = new BlockPos(11, 64, 10);

    private final AtomicLong nextSeed = new AtomicLong();
    private final CloudShardTables tables = new CloudShardTables(nextSeed::incrementAndGet);

    @Test
    void everyFrameOfOneCloudReadsTheTableItsFirstFrameDrew() {
        ShardTable firstFrame = tables.tableAt(MARKER);
        ShardTable secondFrame = tables.tableAt(MARKER.mutable());

        assertSame(firstFrame, secondFrame);
    }

    @Test
    void twoCloudsABlockApartDrawTwoLayouts() {
        ShardTable here = tables.tableAt(MARKER);
        ShardTable there = tables.tableAt(NEIGHBOR);

        assertNotEquals(here.center(0), there.center(0));
    }

    @Nested
    class OnceTheCloudEnds {

        @Test
        void aCloudThatStoppedRenderingDrawsANewTable() {
            ShardTable before = tables.tableAt(MARKER);

            tables.dropAt(MARKER);

            ShardTable after = tables.tableAt(MARKER);
            assertNotSame(before, after);
            assertNotEquals(before.center(0), after.center(0));
        }

        @Test
        void aRemovedMarkerDrawsANewTableWhileItsNeighborKeepsItsOwn() {
            ShardTable removed = tables.tableAt(MARKER);
            ShardTable kept = tables.tableAt(NEIGHBOR);

            tables.retainClouds(NEIGHBOR::equals);

            assertNotSame(removed, tables.tableAt(MARKER));
            assertSame(kept, tables.tableAt(NEIGHBOR));
        }

        @Test
        void leavingTheLevelDrawsNewTables() {
            ShardTable before = tables.tableAt(MARKER);

            tables.dropAll();

            assertNotSame(before, tables.tableAt(MARKER));
        }
    }
}
