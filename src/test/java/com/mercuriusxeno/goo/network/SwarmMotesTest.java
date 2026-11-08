package com.mercuriusxeno.goo.network;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * With no mob bitten, Decay's gnats converge on the face of the block the
 * crosshair rests on, each to a point just off that face, never inside the
 * block where the line to it would strike the face short of the block; with
 * no block under the crosshair, on the point at the stream's reach (decision
 * decay-gnats-degrade-each-block-once).
 */
class SwarmMotesTest {

    private static final BlockPos CROSSHAIR_STONE = new BlockPos(5, 0, 0);
    private static final Vec3 REACH_END = new Vec3(6.5, 1.6, 0.5);
    private static final double TOP = 1.0;
    private static final double DELTA = 1e-9;

    @Test
    void onATopFaceTheGnatsConvergeOnTheFaceItself() {
        BlockHitResult onTheTop = new BlockHitResult(new Vec3(5.5, TOP, 0.5), Direction.UP, CROSSHAIR_STONE, false);
        AABB target = SwarmMotes.moteTarget(onTheTop);
        assertTrue(target.minY >= TOP && target.maxY - TOP < SwarmMotes.FACE_LIFT + DELTA,
                "the gnats aim at heights " + target.minY + " to " + target.maxY + ", the face stands at " + TOP);
        assertEquals(new AABB(CROSSHAIR_STONE).minX, target.minX, DELTA);
        assertEquals(new AABB(CROSSHAIR_STONE).maxZ, target.maxZ, DELTA);
    }

    @Test
    void onASideFaceTheGnatsConvergeOnThatFace() {
        BlockHitResult onTheWest = new BlockHitResult(new Vec3(5, 0.5, 0.5), Direction.WEST, CROSSHAIR_STONE, false);
        AABB target = SwarmMotes.moteTarget(onTheWest);
        assertTrue(target.maxX <= CROSSHAIR_STONE.getX() && CROSSHAIR_STONE.getX() - target.minX < SwarmMotes.FACE_LIFT
                + DELTA, "the gnats aim at x " + target.minX + " to " + target.maxX);
    }

    @Test
    void withNoBlockUnderTheCrosshairTheyConvergeOnTheReachsEnd() {
        BlockHitResult miss = BlockHitResult.miss(REACH_END, Direction.UP, BlockPos.containing(REACH_END));
        assertEquals(new AABB(REACH_END, REACH_END), SwarmMotes.moteTarget(miss));
    }
}
