package com.mercuriusxeno.goo.ability.program;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.List;
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
}
