package com.mercuriusxeno.goo.ability.program;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Fungal Shift's aim snaps to a fungus block within three degrees of the
 * look and within the reach, nearest the look first, and to nothing wider
 * or farther (decision fungal-shift-blinks-to-the-aimed-fungus).
 */
class FungusAimTest {

    private static final Vec3 EYE = new Vec3(0.5, 1.5, 0.5);
    private static final Vec3 EAST = new Vec3(1, 0, 0);
    private static final double REACH = 64;

    @Test
    void aPointTwoDegreesOffAtSixtyBlocksIsWithinTheSnap() {
        double off = Math.tan(Math.toRadians(2)) * 60;
        assertTrue(FungusAim.withinSnap(EYE, EAST, EYE.add(60, 0, off), REACH));
    }

    @Test
    void aPointFourDegreesOffOrPastTheReachIsNot() {
        double off = Math.tan(Math.toRadians(4)) * 30;
        assertFalse(FungusAim.withinSnap(EYE, EAST, EYE.add(30, 0, off), REACH));
        assertFalse(FungusAim.withinSnap(EYE, EAST, EYE.add(REACH + 1, 0, 0), REACH));
    }

    @Test
    void candidatesComeNearestTheLookFirstAndOnlyFungus() {
        BlockPos onTheLine = new BlockPos(40, 1, 0);
        BlockPos besideIt = new BlockPos(40, 1, 1);
        BlockPos farOff = new BlockPos(40, 1, 9);
        Set<BlockPos> fungus = Set.of(besideIt, onTheLine, farOff);

        List<BlockPos> candidates = FungusAim.snapCandidates(EYE, EAST, REACH, fungus::contains);

        assertEquals(List.of(onTheLine, besideIt), candidates);
    }

    @Test
    void aFungusWithinTheNearReachOfTheFeetCountsAndOnePastItDoesNot() {
        BlockPos twoEast = new BlockPos(2, 0, 0);
        BlockState mushroom = mock(BlockState.class);
        when(mushroom.is(ShiftStep.FUNGUS)).thenReturn(true);
        BlockState air = mock(BlockState.class);
        Level level = mock(Level.class);
        when(level.getBlockState(any(BlockPos.class))).thenAnswer(call ->
                call.<BlockPos>getArgument(0).equals(twoEast) ? mushroom : air);
        Entity shifter = mock(Entity.class);
        when(shifter.position()).thenReturn(new Vec3(0.5, 0, 0.5));
        when(shifter.blockPosition()).thenReturn(BlockPos.ZERO);

        assertEquals(List.of(true, false), List.of(FungusAim.standsNearFungus(level, shifter, 3),
                FungusAim.standsNearFungus(level, shifter, 1)));
    }
}