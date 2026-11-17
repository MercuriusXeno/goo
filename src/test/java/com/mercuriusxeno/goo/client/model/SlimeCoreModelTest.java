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
                ChunkSectionLayer.TRANSLUCENT, null, 0, true, 0, false);
        BakedQuad shellTop = SlimeCoreQuads.faceOf(new SlimeCoreBox(0, 0, 0, 120, 8, 120), Direction.UP, layers);
        BlockStateModel shell = mock(BlockStateModel.class);
        BlockStateModelPart shellPart = mock(BlockStateModelPart.class);
        when(shellPart.getQuads(any())).thenReturn(List.of());
        when(shellPart.getQuads(null)).thenReturn(List.of(shellTop));
        doAnswer(call -> call.<List<BlockStateModelPart>>getArgument(4).add(shellPart))
                .when(shell).collectParts(any(), any(), any(), any(), any());
        BakedQuad.MaterialInfo core = new BakedQuad.MaterialInfo(mock(TextureAtlasSprite.class),
                ChunkSectionLayer.TRANSLUCENT, null, VolumeCellTint.CORE_TINT_INDEX, true, 0, false);
        List<BlockStateModelPart> parts = new ArrayList<>();

        new SlimeCoreModel(shell, core, core, Map.of()).collectParts(level, CELL, cell, RandomSource.create(0), parts);

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
