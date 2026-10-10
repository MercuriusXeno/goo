package com.mercuriusxeno.goo.client.ability;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A block transform runs from nothing to whole over its duration, ends after
 * it, and a new transform on a block replaces the one playing there; its
 * progress rides the overlay coordinates in the shader's units
 * (decision petrify-stone-encasement-and-calcify-map).
 */
class BlockTransformsTest {

    private static final BlockPos GRAVEL = new BlockPos(3, 64, 2);
    private static final long START = 100L;
    private static final float DELTA = 1e-6f;
    private static final int MAROON = 0xC03434;

    @Test
    void progressRunsOverTheDuration() {
        BlockTransforms.Transform transform = new BlockTransforms.Transform(GRAVEL, null, START);
        assertEquals(0f, transform.progress(START), DELTA);
        assertEquals(0.5f, transform.progress(START + BlockTransforms.DURATION_TICKS / 2f), DELTA);
        assertEquals(1f, transform.progress(START + 2f * BlockTransforms.DURATION_TICKS), DELTA);
    }

    @Test
    void aTransformEndsAfterItsDurationAndANewOneReplacesTheOld() {
        BlockTransforms transforms = new BlockTransforms();
        transforms.start(GRAVEL, null, START);
        transforms.start(GRAVEL, null, START + 5);
        assertEquals(1, transforms.live(START + 5).size());
        assertEquals(0, transforms.live(START + 5 + BlockTransforms.DURATION_TICKS).size());
    }

    // decay-gnats-degrade-each-block-once: Decay's maroon face carries into the step rather than vanishing
    @Test
    void aStepOnATintedExposureDissolvesFromTheTintedFace() {
        BlockTransforms transforms = new BlockTransforms();
        transforms.expose(GRAVEL, null, 0.9f, START, MAROON);
        transforms.start(GRAVEL, null, START + 1);
        assertEquals(MAROON, transforms.live(START + 1).getFirst().tint());
        assertEquals(0, transforms.exposing(START + 1).size());
    }

    @Test
    void aStepWithNoTintedExposureDissolvesUntinted() {
        BlockTransforms transforms = new BlockTransforms();
        transforms.expose(GRAVEL, null, 0.9f, START, BlockTransforms.UNTINTED);
        transforms.start(GRAVEL, null, START + 1);
        assertEquals(BlockTransforms.UNTINTED, transforms.live(START + 1).getFirst().tint());
    }

    @Test
    void progressRidesTheOverlayCoordinatesInShaderUnits() {
        assertEquals(0, BlockMingleRenderer.progressCoords(0f));
        assertEquals(BlockMingleRenderer.PROGRESS_UNITS / 2, BlockMingleRenderer.progressCoords(0.5f));
        assertEquals(BlockMingleRenderer.PROGRESS_UNITS, BlockMingleRenderer.progressCoords(2f));
    }
}
