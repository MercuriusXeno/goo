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
 * no block under the crosshair, through a block-wide cloud around the stream's reach (decision
 * decay-gnats-degrade-each-block-once).
 */
class SwarmMotesTest {

    private static final BlockPos CROSSHAIR_STONE = new BlockPos(5, 0, 0);
    private static final Vec3 REACH_END = new Vec3(6.5, 1.6, 0.5);
    private static final double TOP = 1.0;
    private static final double DELTA = 1e-9;
    /** The least width of the cloud a missing swarm spreads through, on every axis. */
    private static final double CLOUD_WIDTH = 1.0;

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
    void withNoBlockUnderTheCrosshairTheySpreadThroughACloudAroundTheReachsEnd() {
        BlockHitResult miss = BlockHitResult.miss(REACH_END, Direction.UP, BlockPos.containing(REACH_END));

        AABB target = SwarmMotes.moteTarget(miss);

        // operator ruling 2026-11-13: a swept cast's gnats ended on one point, a straight line in midair
        assertTrue(target.contains(REACH_END), "the cloud should hold the reach's end, spans " + target);
        assertTrue(target.getXsize() >= CLOUD_WIDTH && target.getYsize() >= CLOUD_WIDTH
                && target.getZsize() >= CLOUD_WIDTH, "the gnats should spread through a block-wide cloud, span "
                + target);
    }
}
