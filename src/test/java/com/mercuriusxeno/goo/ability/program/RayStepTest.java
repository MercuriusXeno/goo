package com.mercuriusxeno.goo.ability.program;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
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
 * RayStep's cadence and what it strikes: the hold's first tick hits and then
 * one in every ten, and the ray strikes a block by its collision shape, so it
 * passes through a sign.
 */
class RayStepTest {

    private static final RayStep RAY = new RayStep(32, 10, List.of(), List.of(), List.of());

    /** A sign stands an outline and no collision, so the ray passes through it to its reach. */
    @Test
    void theRayPassesThroughABlockStandingNoCollision() {
        BlockState sign = mock(BlockState.class);
        when(sign.getShape(any(BlockGetter.class), any(BlockPos.class))).thenReturn(Shapes.block());
        when(sign.getCollisionShape(any(BlockGetter.class), any(BlockPos.class))).thenReturn(Shapes.empty());
        BlockGetter signs = mock(BlockGetter.class);
        when(signs.getBlockState(any(BlockPos.class))).thenReturn(sign);
        Vec3 reach = new Vec3(4.5, 0.5, 0.5);

        HitResult hit = RayStep.clipStriking(signs, new Vec3(0.5, 0.5, 0.5), reach);

        assertEquals(HitResult.Type.MISS, hit.getType());
        assertEquals(reach, hit.getLocation());
    }

    @Test
    void hitsOnTheFirstTickThenEveryTenth() {
        assertTrue(RAY.hitsOn(1));
        assertFalse(RAY.hitsOn(2));
        assertFalse(RAY.hitsOn(10));
        assertTrue(RAY.hitsOn(11));
    }
}
