package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.network.HoldMarks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.Optional;
import java.util.OptionalInt;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A painted block steps through the finish its stream names, whether the
 * hold fills it or it is left to finish alone past nine tenths, a block
 * stepping to the empty block included
 * (decision blocks-that-cook-into-items-cook-like-blocks).
 */
class PaintedHoldTest {

    private static final float RATE = 0.05f;

    private final ChannelHost host = mock(ChannelHost.class);
    private final BlockPos pos = new BlockPos(3, 1, 2);
    private final Block log = mock(Block.class);
    private final Block empty = mock(Block.class);
    private final BlockState emptyState = mock(BlockState.class);
    private final PaintedHold.CellFinish finish = mock(PaintedHold.CellFinish.class);
    private final HoldMarks marks = new HoldMarks();

    PaintedHoldTest() {
        when(empty.defaultBlockState()).thenReturn(emptyState);
        when(emptyState.isAir()).thenReturn(true);
        when(host.blockAt(pos)).thenReturn(log);
        marks.paint(pos, log);
    }

    @Test
    void aBlockPastNineTenthsTowardTheEmptyBlockFinishesAloneThroughItsFinish() {
        when(host.exposeBlock(pos, emptyState, RATE)).thenReturn(0.95f);

        PaintedHold.buildPainted(host, raw -> Optional.of(empty), marks, RATE, OptionalInt.empty(), finish);

        ArgumentCaptor<Runnable> alone = ArgumentCaptor.forClass(Runnable.class);
        verify(host).finishBlockAlone(eq(pos), eq(RATE), alone.capture());
        verify(finish, never()).finish(any(), any(), any());
        alone.getValue().run();
        verify(finish).finish(pos, log, empty);
    }

    @Test
    void aBlockTheHoldFillsStepsThroughItsFinish() {
        when(host.exposeBlock(pos, emptyState, RATE)).thenReturn(1f);

        PaintedHold.buildPainted(host, raw -> Optional.of(empty), marks, RATE, OptionalInt.empty(), finish);

        verify(finish).finish(pos, log, empty);
        verify(host, never()).transformBlock(any(), any());
    }
}
