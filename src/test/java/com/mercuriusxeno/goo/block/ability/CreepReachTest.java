package com.mercuriusxeno.goo.block.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SculkBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

/**
 * A charge on Creep goo is stranded where no Creep cell it can reach turns its
 * block and no step leads off the goo, however wide the coat: a coat wider
 * than any bound a walk might set still strands its charge, and one sculk
 * neighbour at its far corner frees it
 * (decision sculk-charge-runs-along-host-goo-without-weakening, criterion 3851).
 */
class CreepReachTest {

    /** A coat 40 cells on a side holds 1600 cells, more than the 1024 the walk once stopped at. */
    private static final int SIDE = 40;
    private static final int COAT_Y = 64;
    private static final BlockPos START = new BlockPos(0, COAT_Y, 0);
    private static final BlockPos FAR_CORNER = new BlockPos(SIDE, COAT_Y, SIDE - 1);

    private final BlockState host = stateOf(mock(CreepCellBlock.class));
    private final BlockState stone = stateOf(mock(Block.class));
    private final BlockState sculk = stateOf(mock(Block.class, withSettings().extraInterfaces(SculkBehaviour.class)));

    @Test
    void aStrandedCoatWiderThanAThousandCellsStrandsItsCharge() {
        assertTrue(CreepReach.walk(coat(false), START, cell -> false).stranded());
    }

    @Test
    void oneSculkNeighbourAtTheFarCornerFreesTheCharge() {
        assertFalse(CreepReach.walk(coat(true), START, cell -> false).stranded());
    }

    /**
     * A flat coat of Host cells SIDE by SIDE on stone, stone all around it,
     * with or without one block of sculk beside its far corner.
     */
    private LevelAccessor coat(boolean sculkAtTheFarCorner) {
        LevelAccessor level = mock(LevelAccessor.class);
        when(level.getBlockState(any(BlockPos.class))).thenAnswer(read -> {
            BlockPos pos = read.getArgument(0);
            if (sculkAtTheFarCorner && pos.equals(FAR_CORNER)) {
                return sculk;
            }
            boolean inCoat = pos.getY() == COAT_Y && pos.getX() >= 0 && pos.getX() < SIDE
                    && pos.getZ() >= 0 && pos.getZ() < SIDE;
            return inCoat ? host : stone;
        });
        return level;
    }

    /** A state of a block, sturdy on every face, so no edge step passes a corner. */
    private static BlockState stateOf(Block block) {
        BlockState state = mock(BlockState.class);
        when(state.getBlock()).thenReturn(block);
        when(state.isFaceSturdy(any(), any(), any())).thenReturn(true);
        return state;
    }
}
