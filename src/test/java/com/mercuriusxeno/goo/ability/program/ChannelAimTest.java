package com.mercuriusxeno.goo.ability.program;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Flatten's swath lies out from the face the hold began on, ground or wall,
 * never behind it: the 3x3 in line with the aimed block, 3 layers out, the
 * outermost first; and the aimed block is the one behind the face the cursor
 * rests on (decision flatten-disc-cursor-breaks-above-the-plane).
 */
class ChannelAimTest {

    private static final BlockPos GROUND = new BlockPos(10, 63, 0);
    private static final BlockPos WALL = new BlockPos(14, 64, 0);

    @Nested
    class Swath {

        @Test
        void onTheGroundItRisesThreeAboveTheTopOutermostFirst() {
            ChannelAim aim = new ChannelAim(Vec3.ZERO, new ChannelAim.FacePlane(GROUND, Direction.UP));
            List<BlockPos> swath = aim.swathOutermostFirst(new BlockPos(11, 70, 1));
            assertEquals(27, swath.size());
            assertEquals(66, swath.getFirst().getY());
            assertEquals(64, swath.getLast().getY());
            assertTrue(swath.contains(new BlockPos(10, 64, 0)));
            assertTrue(swath.contains(new BlockPos(12, 66, 2)));
        }

        @Test
        void onAWestFaceItRunsWestNeverBehind() {
            ChannelAim aim = new ChannelAim(Vec3.ZERO, new ChannelAim.FacePlane(WALL, Direction.WEST));
            List<BlockPos> swath = aim.swathOutermostFirst(new BlockPos(20, 65, 1));
            assertEquals(27, swath.size());
            assertEquals(11, swath.getFirst().getX());
            assertEquals(13, swath.getLast().getX());
            assertTrue(swath.stream().allMatch(pos -> pos.getX() < WALL.getX()));
            assertTrue(swath.contains(new BlockPos(13, 66, 2)));
        }

        @Test
        void aSliceBreaksItsRingInTurnAndItsMiddleLast() {
            BlockPos middle = new BlockPos(1, 2, 0);
            List<BlockPos> slice = ChannelAim.sliceRingIn(middle, Direction.Axis.X);
            assertEquals(9, Set.copyOf(slice).size());
            assertEquals(middle, slice.getLast());
            assertTrue(slice.stream().allMatch(pos -> pos.getX() == middle.getX()
                    && Math.abs(pos.getY() - middle.getY()) <= 1 && Math.abs(pos.getZ() - middle.getZ()) <= 1));
        }

        @Test
        void aHoldThatBeganOnNoFaceHasNoSwath() {
            assertTrue(new ChannelAim(Vec3.ZERO, null).swathOutermostFirst(GROUND).isEmpty());
        }

        @Test
        void theDiscLiesInTheCellJustOutFromTheFace() {
            ChannelAim.FacePlane wall = new ChannelAim.FacePlane(WALL, Direction.WEST);
            assertEquals(new BlockPos(13, 70, 5), wall.cellOutFrom(new BlockPos(2, 70, 5)));
        }
    }

    @Nested
    class AimedBlock {

        @Test
        void westFaceAimReadsTheBlockBehindTheFace() {
            ChannelAim aim = new ChannelAim(new Vec3(10, 64.5, 0.5), null);

            assertEquals(new BlockPos(10, 64, 0), aim.aimedBlock(new Vec3(8, 65.6, 0.5)));
        }

        @Test
        void topFaceAimFromAboveReadsTheBlockUnderTheFace() {
            ChannelAim aim = new ChannelAim(new Vec3(10.5, 64, 0.5), null);

            assertEquals(new BlockPos(10, 63, 0), aim.aimedBlock(new Vec3(8.5, 65.6, 0.5)));
        }
    }

    /**
     * A held tick's aim point: a locking ability aims at the mob the aim
     * assist locks, and every other hold aims at the crosshair
     * (decision sunbeam-lands-with-impact-and-aim).
     */
    @Nested
    class HeldAimPoint {

        private static final Vec3 LOCKED_MOB = new Vec3(5, 2, 5);
        private static final Vec3 CROSSHAIR = new Vec3(3, 2, 4);

        @Test
        void aLockingHoldAimsAtTheLockedMob() {
            assertEquals(LOCKED_MOB, ChannelAim.heldAimPoint(true, LOCKED_MOB, CROSSHAIR));
        }

        @Test
        void aHoldThatDoesNotLockAimsAtTheCrosshair() {
            assertEquals(CROSSHAIR, ChannelAim.heldAimPoint(false, LOCKED_MOB, CROSSHAIR));
        }

        @Test
        void aLockingHoldWithNoMobLockedAimsAtTheCrosshair() {
            assertEquals(CROSSHAIR, ChannelAim.heldAimPoint(true, null, CROSSHAIR));
        }
    }
}
