package com.mercuriusxeno.goo.client.model;

import com.mercuriusxeno.goo.block.ability.VolumeCellBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Covers how the slime core model reads a cell's neighbourhood from the
 * level: a cell of its own goo lends its layers, and a cell of another goo,
 * any other block or open air lends none.
 */
class SlimeCoreModelTest {

    private static final BlockPos CELL = new BlockPos(4, 64, 4);

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
}
