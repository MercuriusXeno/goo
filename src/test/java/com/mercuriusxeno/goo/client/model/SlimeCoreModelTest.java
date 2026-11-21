package com.mercuriusxeno.goo.client.model;

import com.mercuriusxeno.goo.block.ability.VolumeCellBlock;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers how the slime core model reads a cell's neighbourhood from the
 * level: a cell of its own goo lends its layers, and a cell of another goo,
 * any other block or open air lends none; and how it draws the shell's top
 * over the core so the translucent sort puts the core first.
 */
class SlimeCoreModelTest {

    private static final BlockPos CELL = new BlockPos(4, 64, 4);
    private static final float EPSILON = 1e-5f;
    private static final float LAYER_PIXELS = 16f / 15;

    private static BlockState cellOf(Block goo, int layers) {
        BlockState state = mock(BlockState.class);
        when(state.getBlock()).thenReturn(goo);
        when(state.is(goo)).thenReturn(true);
        when(state.getValue(VolumeCellBlock.LAYERS)).thenReturn(layers);
        return state;
    }

    @Test
    void onlyACellOfItsOwnGooLendsItsLayers() {
        VolumeCellBlock slime = mock(VolumeCellBlock.class);
        VolumeCellBlock slick = mock(VolumeCellBlock.class);
        BlockState self = cellOf(slime, 15);
        BlockState sameGoo = cellOf(slime, 8);
        BlockState otherGoo = cellOf(slick, 15);
        BlockState air = mock(BlockState.class);
        BlockGetter level = mock(BlockGetter.class);
        when(level.getBlockState(any())).thenReturn(air);
        when(level.getBlockState(CELL.east())).thenReturn(sameGoo);
        when(level.getBlockState(CELL.west())).thenReturn(otherGoo);

        LayersAround around = SlimeCoreModel.layersAround(level, CELL, self);

        assertEquals(15, around.layersAt(0, 0, 0));
        assertEquals(8, around.layersAt(1, 0, 0));
        assertEquals(0, around.layersAt(-1, 0, 0), "a cell of another goo");
        assertEquals(0, around.layersAt(0, 0, 1), "open air");
    }

    /**
     * Translucent quads draw farthest centre first, so a core piece whose
     * centre stood nearer than its shell's would draw after the shell and
     * fail the depth the shell wrote: each core top piece stands under a
     * shell top piece of its own extent, nearer the eye from any view above.
     * slime-core-draws-translucent-at-the-shells-opacity
     */
    @Test
    void everyCoreTopPieceStandsUnderAShellTopPieceOfItsExtent() {
        BlockState cell = cellOf(mock(VolumeCellBlock.class), 1);
        BlockAndTintGetter level = mock(BlockAndTintGetter.class);
        BlockState air = mock(BlockState.class);
        when(level.getBlockState(any())).thenAnswer(call -> {
            BlockPos at = call.getArgument(0);
            return at.getY() == CELL.getY() && Math.abs(at.getX() - CELL.getX()) <= 1
                    && Math.abs(at.getZ() - CELL.getZ()) <= 1 ? cell : air;
        });
        BakedQuad.MaterialInfo layers = new BakedQuad.MaterialInfo(mock(TextureAtlasSprite.class),
                ChunkSectionLayer.TRANSLUCENT, null, null, null, 0, null, 0, false);
        BlockStateModel shell = mock(BlockStateModel.class);
        BakedQuad.MaterialInfo core = new BakedQuad.MaterialInfo(mock(TextureAtlasSprite.class),
                ChunkSectionLayer.TRANSLUCENT, null, null, null, VolumeCellTint.CORE_TINT_INDEX, null, 0, false);
        List<BlockStateModelPart> parts = new ArrayList<>();

        new SlimeCoreModel(shell, core, layers, Map.of()).collectParts(level, CELL, cell, RandomSource.create(0), parts);

        List<BakedQuad> tops = new ArrayList<>();
        for (BlockStateModelPart part : parts) {
            part.getQuads(null).stream().filter(quad -> quad.direction() == Direction.UP).forEach(tops::add);
        }
        List<BakedQuad> coreTops = tops.stream().filter(quad -> quad.materialInfo() == core).toList();
        List<BakedQuad> shellTops = tops.stream().filter(quad -> quad.materialInfo() == layers).toList();
        assertFalse(coreTops.isEmpty(), "the core should draw a top");
        for (BakedQuad coreTop : coreTops) {
            assertTrue(shellTops.stream().anyMatch(shellPiece -> sameExtentAbove(shellPiece, coreTop)),
                    "no shell top piece stands over the core top at " + extentList(coreTop));
        }
    }

    /**
     * The drips over an edge hang from the sloped top's edge, not from the
     * flat layer height their model stands at: a block of 8 alone hangs its
     * north drips from 4 layers, the height its top slopes to at that edge.
     * goo-block-top-slopes-to-the-average-with-the-adjoining-block
     */
    @Test
    void dripsHangFromTheSlopedTopsEdge() {
        BlockState cell = cellOf(mock(VolumeCellBlock.class), 8);
        BlockAndTintGetter level = mock(BlockAndTintGetter.class);
        when(level.getBlockState(any())).thenReturn(mock(BlockState.class));
        BakedQuad.MaterialInfo drip = new BakedQuad.MaterialInfo(mock(TextureAtlasSprite.class),
                ChunkSectionLayer.TRANSLUCENT, null, null, null, 0, null, 0, false);
        BakedQuad band = SlimeCoreQuads.faceOf(new Vector3f(0, 8 * LAYER_PIXELS - 3, -0.05f),
                new Vector3f(16, 8 * LAYER_PIXELS, -0.05f), Direction.NORTH, drip);
        BlockStateModelPart dripPart = mock(BlockStateModelPart.class);
        when(dripPart.getQuads(any())).thenReturn(List.of());
        when(dripPart.getQuads(null)).thenReturn(List.of(band));
        BlockStateModel drips = mock(BlockStateModel.class);
        doAnswer(call -> call.<List<BlockStateModelPart>>getArgument(4).add(dripPart))
                .when(drips).collectParts(any(), any(), any(), any(), any());
        BakedQuad.MaterialInfo film = new BakedQuad.MaterialInfo(mock(TextureAtlasSprite.class),
                ChunkSectionLayer.TRANSLUCENT, null, null, null, 0, null, 0, false);
        List<BlockStateModelPart> parts = new ArrayList<>();

        new SlimeCoreModel(mock(BlockStateModel.class), film, film, Map.of(Direction.NORTH, drips))
                .collectParts(level, CELL, cell, RandomSource.create(0), parts);

        float top = 0;
        for (BlockStateModelPart part : parts) {
            for (BakedQuad quad : part.getQuads(null)) {
                for (int vertex = 0; quad.materialInfo() == drip && vertex < BakedQuad.VERTEX_COUNT; vertex++) {
                    top = Math.max(top, quad.position(vertex).y());
                }
            }
        }
        assertEquals(4f / 15, top, EPSILON, "the drips' top should stand at the edge's 4 layers");
    }

    /**
     * A block on a full block of its goo is a column's top: its goo runs on
     * down the column, so it hangs no drips over its edges.
     * stacked-goo-cells-share-one-slime-core
     */
    @Test
    void aColumnsTopHangsNoDrips() {
        Block goo = mock(VolumeCellBlock.class);
        BlockState cell = cellOf(goo, 5);
        BlockState full = cellOf(goo, 15);
        BlockAndTintGetter level = mock(BlockAndTintGetter.class);
        when(level.getBlockState(any())).thenReturn(mock(BlockState.class));
        when(level.getBlockState(CELL.below())).thenReturn(full);
        BlockStateModel drips = mock(BlockStateModel.class);
        BakedQuad.MaterialInfo film = new BakedQuad.MaterialInfo(mock(TextureAtlasSprite.class),
                ChunkSectionLayer.TRANSLUCENT, null, null, null, 0, null, 0, false);

        new SlimeCoreModel(mock(BlockStateModel.class), film, film, Map.of(Direction.NORTH, drips))
                .collectParts(level, CELL, cell, RandomSource.create(0), new ArrayList<>());

        verify(drips, never()).collectParts(any(), any(), any(), any(), any());
    }

    /**
     * The break lines vanilla draws over a block being broken, as the vacuum
     * breaks a goo block, read the model with no level: there the model
     * draws its block's box at the volume its block state names, so the
     * cracks stand on the goo.
     */
    // vanilla's break lines read the model through the deprecated level-less overload, the path under test
    @SuppressWarnings("deprecation")
    @Test
    void theBreakLinesFindTheBlocksBox() {
        BakedQuad.MaterialInfo film = new BakedQuad.MaterialInfo(mock(TextureAtlasSprite.class),
                ChunkSectionLayer.TRANSLUCENT, null, null, null, 0, null, 0, false);
        List<BlockStateModelPart> parts = new ArrayList<>();

        new SlimeCoreModel(mock(BlockStateModel.class), film, film, Map.of(), 8)
                .collectParts(RandomSource.create(0), parts);

        List<BakedQuad> quads = parts.stream().flatMap(part -> part.getQuads(null).stream()).toList();
        assertTrue(quads.stream().anyMatch(quad -> quad.direction() == Direction.UP
                        && Math.abs(quad.position(0).y() - 8f / 15) < EPSILON),
                "the break lines should find a top at the block's 8 layers");
        assertEquals(6, quads.stream().map(BakedQuad::direction).distinct().count(), "every face of the box");
    }

    private static boolean sameExtentAbove(BakedQuad shell, BakedQuad core) {
        float[] over = extentOf(shell);
        float[] under = extentOf(core);
        return Math.abs(over[0] - under[0]) < EPSILON && Math.abs(over[1] - under[1]) < EPSILON
                && Math.abs(over[2] - under[2]) < EPSILON && Math.abs(over[3] - under[3]) < EPSILON
                && over[4] > under[4];
    }

    /** A top quad's west, east, north and south bounds and its height. */
    private static List<Float> extentList(BakedQuad quad) {
        float[] extent = extentOf(quad);
        return List.of(extent[0], extent[1], extent[2], extent[3], extent[4]);
    }

    private static float[] extentOf(BakedQuad quad) {
        float[] extent = {Float.MAX_VALUE, -Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, 0};
        for (int vertex = 0; vertex < BakedQuad.VERTEX_COUNT; vertex++) {
            Vector3fc corner = quad.position(vertex);
            extent[0] = Math.min(extent[0], corner.x());
            extent[1] = Math.max(extent[1], corner.x());
            extent[2] = Math.min(extent[2], corner.z());
            extent[3] = Math.max(extent[3], corner.z());
            extent[4] = corner.y();
        }
        return extent;
    }
}
