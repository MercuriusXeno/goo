package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.block.ability.PrismBlock;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * RayStep's cadence and a prism's split: the hold's first tick hits and then
 * one in every ten; a refracted hit is raised by its factor and thinned by
 * the target count to its exponent, costing each target only a little;
 * the ray passes through blocks with no collision and stops on a prism's outline.
 * decision sunbeam-splits-at-the-prism-with-a-glisten
 */
class RayStepTest {

    private static final RayStep.Refraction SUNBEAM = new RayStep.Refraction(16, 1.5, 0.85);
    private static final RayStep RAY = new RayStep(32, 10, List.of(), SUNBEAM, List.of(), List.of());
    private static final double TOLERANCE = 1e-9;

    /** A sign stands an outline and no collision. */
    private static BlockState sign() {
        BlockState state = mock(BlockState.class);
        when(state.getBlock()).thenReturn(mock(Block.class));
        when(state.getShape(any(BlockGetter.class), any(BlockPos.class))).thenReturn(Shapes.block());
        when(state.getCollisionShape(any(BlockGetter.class), any(BlockPos.class))).thenReturn(Shapes.empty());
        return state;
    }

    private static BlockState wall() {
        BlockState state = mock(BlockState.class);
        when(state.getBlock()).thenReturn(mock(Block.class));
        when(state.getShape(any(BlockGetter.class), any(BlockPos.class))).thenReturn(Shapes.block());
        when(state.getCollisionShape(any(BlockGetter.class), any(BlockPos.class))).thenReturn(Shapes.block());
        return state;
    }

    @Test
    void theRayPassesThroughASignToTheWallBehindIt() {
        BlockPos signAt = new BlockPos(0, 0, 3);
        BlockPos wallAt = new BlockPos(0, 0, 6);
        BlockState sign = sign();
        BlockState wall = wall();
        BlockState air = mock(BlockState.class);
        when(air.getBlock()).thenReturn(mock(Block.class));
        when(air.getShape(any(BlockGetter.class), any(BlockPos.class))).thenReturn(Shapes.empty());
        when(air.getCollisionShape(any(BlockGetter.class), any(BlockPos.class))).thenReturn(Shapes.empty());
        BlockGetter level = mock(BlockGetter.class);
        when(level.getBlockState(any(BlockPos.class))).thenAnswer(call -> {
            BlockPos pos = call.getArgument(0);
            return pos.equals(signAt) ? sign : pos.equals(wallAt) ? wall : air;
        });
        BlockHitResult hit = RayStep.clipStriking(level, new Vec3(0.5, 0.5, 0.5), new Vec3(0.5, 0.5, 10));
        assertEquals(HitResult.Type.BLOCK, hit.getType());
        assertEquals(wallAt, hit.getBlockPos(), "the ray stopped on the sign");
    }

    @Test
    void theRayStopsOnAPrismsOutline() {
        BlockState prism = mock(BlockState.class);
        when(prism.getBlock()).thenReturn(mock(PrismBlock.class));
        when(prism.getShape(any(BlockGetter.class), any(BlockPos.class))).thenReturn(Shapes.block());
        when(prism.getCollisionShape(any(BlockGetter.class), any(BlockPos.class))).thenReturn(Shapes.empty());
        assertEquals(Shapes.block(), RayStep.strikingShape(prism, mock(BlockGetter.class), BlockPos.ZERO));
    }

    @Test
    void hitsOnTheFirstTickThenEveryTenth() {
        assertTrue(RAY.hitsOn(1));
        assertFalse(RAY.hitsOn(2));
        assertFalse(RAY.hitsOn(10));
        assertTrue(RAY.hitsOn(11));
    }

    @Test
    void oneTargetTakesTheWholeRaisedHit() {
        assertEquals(1.5, SUNBEAM.share(1), TOLERANCE);
    }

    @Test
    void eachOfNTargetsTakesTheRaisedHitOverNToTheExponent() {
        assertEquals(1.5 / Math.pow(3, 0.85), SUNBEAM.share(3), TOLERANCE);
    }

    @Test
    void theSplitThinsSlowerThanAnEvenOne() {
        assertTrue(SUNBEAM.share(3) * 3 > SUNBEAM.share(1));
        assertTrue(SUNBEAM.share(3) > 1.5 / 3);
    }

    @Test
    void noTargetTakesNothing() {
        assertEquals(0, SUNBEAM.share(0), TOLERANCE);
    }
}
