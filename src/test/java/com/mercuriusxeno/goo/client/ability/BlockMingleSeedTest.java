package com.mercuriusxeno.goo.client.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Each block transition dissolves through its own field: its seed follows the
 * block and the tick it began, stays steady while an exposure builds, and rides
 * the overlay's V half into block_mingle.vsh (decision diagnose-then-fix-dissolve-repeat).
 */
class BlockMingleSeedTest {

    private static final BlockPos SAND = new BlockPos(3, 64, 2);
    private static final long START = 100L;
    private static final int USES = 32;
    /** Of 32 uses on one block, at least this many draw distinct fields. */
    private static final int MOST_REPEATS_ALLOWED = 8;
    private static final int SOME_SEED = 200;
    private static final int OVERLAY_V_SHIFT = 16;

    @Test
    void usesOnOneBlockAtDifferentTicksDrawDifferentFields() {
        Set<Integer> seeds = new HashSet<>();
        for (int use = 0; use < USES; use++) {
            seeds.add(new BlockTransforms.Transform(SAND, null, START + use * 40L).seed());
        }

        assertTrue(seeds.size() >= USES - MOST_REPEATS_ALLOWED, "only " + seeds.size() + " fields over " + USES + " uses");
    }

    @Test
    void anExposureKeepsItsSeedWhileItBuildsAndTakesANewOneWhenBuiltAgain() {
        BlockState sandstone = mock(BlockState.class);
        BlockTransforms transforms = new BlockTransforms();
        transforms.expose(SAND, sandstone, 0.2f, START, BlockTransforms.UNTINTED);
        int first = transforms.seedOf(transforms.exposureAt(SAND).orElseThrow());
        transforms.expose(SAND, sandstone, 0.6f, START + 5, BlockTransforms.UNTINTED);

        assertEquals(first, transforms.seedOf(transforms.exposureAt(SAND).orElseThrow()));

        transforms.expose(SAND, sandstone, 0f, START + 6, BlockTransforms.UNTINTED);
        transforms.expose(SAND, sandstone, 0.2f, START + 60, BlockTransforms.UNTINTED);

        assertEquals(BlockTransforms.seedAt(SAND, START + 60),
            transforms.seedOf(transforms.exposureAt(SAND).orElseThrow()));
        assertNotEquals(BlockTransforms.seedAt(SAND, START), BlockTransforms.seedAt(SAND, START + 60));
    }

    @Test
    void theSeedRidesTheOverlaysVHalfBesideTheProgress() {
        assertEquals(BlockMingleRenderer.PROGRESS_UNITS / 2 | SOME_SEED << OVERLAY_V_SHIFT,
            BlockMingleRenderer.overlayCoords(0.5f, SOME_SEED));
    }

    @Test
    void theVertexShaderReadsTheSeedFromTheOverlaysV() throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/assets/goo/shaders/core/block_mingle.vsh")) {
            assertNotNull(in);
            assertTrue(new String(in.readAllBytes(), StandardCharsets.UTF_8).contains("mingleSeed = float(UV1.y);"));
        }
    }
}
