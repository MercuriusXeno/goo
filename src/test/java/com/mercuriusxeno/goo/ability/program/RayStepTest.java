package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.block.ability.PrismBlock;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
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
 * the ray strikes a block by its collision shape, so it passes through a sign,
 * and a prism by its outline.
 * decision sunbeam-splits-at-the-prism-with-a-glisten
 */
class RayStepTest {

    private static final RayStep.Refraction LASER = new RayStep.Refraction(16, 1.5, 0.85);
    private static final RayStep RAY = new RayStep(32, 10, List.of(), LASER, List.of(), List.of());
    private static final double TOLERANCE = 1e-9;

    /** A sign stands an outline and no collision, so the ray passes through it. */
    @Test
    void aBlockThatIsNoPrismIsStruckByItsCollisionShape() {
        BlockState sign = mock(BlockState.class);
        when(sign.getBlock()).thenReturn(mock(Block.class));
        when(sign.getShape(any(BlockGetter.class), any(BlockPos.class))).thenReturn(Shapes.block());
        when(sign.getCollisionShape(any(BlockGetter.class), any(BlockPos.class))).thenReturn(Shapes.empty());

        assertEquals(Shapes.empty(), RayStep.strikingShape(sign, mock(BlockGetter.class), BlockPos.ZERO));
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
        assertEquals(1.5, LASER.share(1), TOLERANCE);
    }

    @Test
    void eachOfNTargetsTakesTheRaisedHitOverNToTheExponent() {
        assertEquals(1.5 / Math.pow(3, 0.85), LASER.share(3), TOLERANCE);
    }

    @Test
    void theSplitThinsSlowerThanAnEvenOne() {
        assertTrue(LASER.share(3) * 3 > LASER.share(1));
        assertTrue(LASER.share(3) > 1.5 / 3);
    }

    @Test
    void noTargetTakesNothing() {
        assertEquals(0, LASER.share(0), TOLERANCE);
    }
}
