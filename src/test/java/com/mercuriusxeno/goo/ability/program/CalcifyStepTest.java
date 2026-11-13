package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.network.HoldMarks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Calcify reaches the blocks whose centers stand inside the stream's cone,
 * nearest the apex first (decision petrify-stone-encasement-and-calcify-map).
 */
class CalcifyStepTest {

    private static final Vec3 APEX = new Vec3(0.5, 0.5, 0.5);
    private static final Vec3 REACH = new Vec3(6.5, 0.5, 0.5);
    private static final double CONE = 30;

    @Test
    void blocksOnTheAxisAreInsideNearestFirst() {
        List<BlockPos> inside = CalcifyStep.blocksInCone(APEX, REACH, CONE);
        assertTrue(inside.contains(new BlockPos(3, 0, 0)));
        assertTrue(inside.indexOf(new BlockPos(1, 0, 0)) < inside.indexOf(new BlockPos(5, 0, 0)));
    }

    @Test
    void blocksOffTheConeOrPastTheReachAreOutside() {
        List<BlockPos> inside = CalcifyStep.blocksInCone(APEX, REACH, CONE);
        assertFalse(inside.contains(new BlockPos(3, 3, 0)));
        assertFalse(inside.contains(new BlockPos(7, 0, 0)));
        assertFalse(inside.contains(new BlockPos(-2, 0, 0)));
    }

    @Test
    void aConeWithNoReachHoldsNothing() {
        assertEquals(List.of(), CalcifyStep.blocksInCone(APEX, APEX, CONE));
    }

    /**
     * A block the fog steps is noted stepped and passed over for the rest of the hold, so gravel steps once to
     * cobblestone however long the fog stands on it (decision block-powers-step-each-block-once-per-activation).
     */
    @Test
    void aBlockTheFogStepsIsPassedOverForTheRestOfTheHold() {
        BlockPos gravel = new BlockPos(3, 0, 0);
        Block cobblestone = mock(Block.class);
        BlockState cobblestoneState = mock(BlockState.class);
        when(cobblestone.defaultBlockState()).thenReturn(cobblestoneState);
        ChannelHost host = mock(ChannelHost.class);
        when(host.eye()).thenReturn(APEX);
        when(host.holdMarks()).thenReturn(new HoldMarks());
        when(host.airAt(any())).thenAnswer(call -> !call.<BlockPos>getArgument(0).equals(gravel));
        when(host.blockAt(any())).thenReturn(mock(Block.class));
        when(host.exposeBlock(any(), any(), anyFloat())).thenReturn(1f);
        ChannelAim aim = new ChannelAim(REACH, null, CONE, 1, true);

        CalcifyStep.calcifyCone(host, aim, block -> Optional.of(cobblestone), 1f);
        CalcifyStep.calcifyCone(host, aim, block -> Optional.of(cobblestone), 1f);

        verify(host).transformBlock(gravel, cobblestoneState);
    }
}
