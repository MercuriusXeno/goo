package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.program.DegradeStep;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
    private static final int MAROON = DegradeStep.NETHER_MAROON;

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

    // broil-heat-wave-ring-cooks-ahead: the build was the one dissolve, so the step only holds the built block whole
    @Test
    void aStepOnAnUntintedExposurePlaysNoSecondDissolveAndHoldsTheBuiltBlockWhole() {
        BlockState raw = mock(BlockState.class);
        BlockState built = mock(BlockState.class);
        BlockTransforms transforms = new BlockTransforms();
        transforms.expose(GRAVEL, built, 0.9f, START, BlockTransforms.UNTINTED);
        transforms.start(GRAVEL, raw, START + 1);
        assertEquals(List.of(), transforms.live(START + 1));
        List<BlockTransforms.Exposure> held = transforms.exposing(START + 1);
        assertEquals(1, held.size());
        assertEquals(built, held.getFirst().toward());
        assertEquals(1f, held.getFirst().share(), DELTA);
        assertEquals(0, transforms.exposing(START + 1 + BlockTransforms.BUILT_HOLD_TICKS + 1).size());
    }

    // broil-heat-wave-ring-cooks-ahead: a log cooked into charcoal dissolves away once, from the log
    @Test
    void aStepOnAnExposureTowardAirDissolvesFromTheOldBlock() {
        BlockState log = mock(BlockState.class);
        BlockState air = mock(BlockState.class);
        when(air.isAir()).thenReturn(true);
        BlockTransforms transforms = new BlockTransforms();
        transforms.expose(GRAVEL, air, 0.9f, START, BlockTransforms.UNTINTED);
        transforms.start(GRAVEL, log, START + 1);
        assertEquals(List.of(new BlockTransforms.Transform(GRAVEL, log, START + 1)), transforms.live(START + 1));
        assertEquals(0, transforms.exposing(START + 1).size());
    }

    @Test
    void aStepWithNoExposureDissolvesUntinted() {
        BlockTransforms transforms = new BlockTransforms();
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
