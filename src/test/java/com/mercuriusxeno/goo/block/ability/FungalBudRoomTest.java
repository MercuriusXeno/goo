package com.mercuriusxeno.goo.block.ability;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.loading.FMLLoader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * A cell with a mushroom on one of its four sides has no room for a fungal bud, and a cell with none beside it has
 * room. The vanilla registries stand the mushroom blocks, so a stubbed FML loader answers while they bootstrap.
 */
class FungalBudRoomTest {

    private static final BlockPos CELL = new BlockPos(4, 2, 4);

    @BeforeAll
    static void bootstrapVanillaRegistries() {
        try (MockedStatic<FMLLoader> loader = mockStatic(FMLLoader.class, RETURNS_DEEP_STUBS)) {
            SharedConstants.tryDetectVersion();
            Bootstrap.bootStrap();
        }
    }

    // A level holding grass around the cell, a red mushroom to its east where the flag says so.
    private static LevelReader around(boolean mushroomEast) {
        BlockState grass = mock(BlockState.class);
        when(grass.getBlock()).thenReturn(mock(Block.class));
        BlockState mushroom = mock(BlockState.class);
        when(mushroom.getBlock()).thenReturn(mock(Block.class));
        when(mushroom.is(Blocks.RED_MUSHROOM)).thenReturn(true);
        LevelReader level = mock(LevelReader.class);
        when(level.getBlockState(any(BlockPos.class))).thenAnswer(call ->
                mushroomEast && call.<BlockPos>getArgument(0).equals(CELL.east()) ? mushroom : grass);
        return level;
    }

    @Test
    void aMushroomBesideACellLeavesItNoRoomForABud() {
        assertEquals(List.of(false, true),
                List.of(FungalBudBlock.hasRoomBeside(around(true), CELL), FungalBudBlock.hasRoomBeside(around(false), CELL)));
    }
}
