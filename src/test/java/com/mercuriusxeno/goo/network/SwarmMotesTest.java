package com.mercuriusxeno.goo.network;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * With no mob bitten, Decay's gnats converge on the block under the
 * crosshair, or on the point at the stream's reach where the crosshair meets
 * no block (decision decay-gnats-degrade-each-block-once).
 */
class SwarmMotesTest {

    private static final BlockPos CROSSHAIR_STONE = new BlockPos(5, 1, 0);
    private static final Vec3 REACH_END = new Vec3(6.5, 1.6, 0.5);

    @Test
    void theGnatsConvergeOnTheCrosshairBlock() {
        BlockHitResult onTheStone = new BlockHitResult(new Vec3(5, 1.5, 0.5), Direction.WEST, CROSSHAIR_STONE, false);
        assertEquals(new AABB(CROSSHAIR_STONE), SwarmMotes.moteTarget(onTheStone));
    }

    @Test
    void withNoBlockUnderTheCrosshairTheyConvergeOnTheReachsEnd() {
        BlockHitResult miss = BlockHitResult.miss(REACH_END, Direction.UP, BlockPos.containing(REACH_END));
        assertEquals(new AABB(REACH_END, REACH_END), SwarmMotes.moteTarget(miss));
    }
}
