package com.mercuriusxeno.goo.ability.program;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Flatten's plane rule: a block breaks only when its bottom stands at or over
 * the height the player's feet held when the hold began, so the block the
 * player stood on stays; and the aimed block is the one behind the face the
 * cursor rests on (decision flatten-disc-cursor-breaks-above-the-plane).
 */
class ChannelAimTest {

    private static final double GROUND = 64;
    private static final double ON_A_SLAB = 64.5;

    @Nested
    class PlaneRule {

        @Test
        void blockAtTheFeetBreaks() {
            assertTrue(new ChannelAim(Vec3.ZERO, GROUND).abovePlane(64));
        }

        @Test
        void blockStoodOnStays() {
            assertFalse(new ChannelAim(Vec3.ZERO, GROUND).abovePlane(63));
        }

        @Test
        void slabBlockStoodOnStaysAndTheOneAboveBreaks() {
            ChannelAim aim = new ChannelAim(Vec3.ZERO, ON_A_SLAB);

            assertFalse(aim.abovePlane(64));
            assertTrue(aim.abovePlane(65));
        }
    }

    @Nested
    class CursorArea {

        @Test
        void thePlaneIsTheTopOfTheBlockTheCursorRestsOn() {
            ChannelAim aim = new ChannelAim(Vec3.ZERO, ChannelAim.planeAbove(63));
            assertFalse(aim.abovePlane(63));
            assertTrue(aim.abovePlane(64));
        }

        @Test
        void aTopFaceSpreadsTheAreaLevel() {
            ChannelAim aim = new ChannelAim(new Vec3(10.5, 64, 0.5), GROUND);
            List<BlockPos> area = aim.areaAround(new BlockPos(10, 63, 0));
            assertEquals(9, area.size());
            assertTrue(area.contains(new BlockPos(9, 63, -1)));
            assertTrue(area.contains(new BlockPos(11, 63, 1)));
        }

        @Test
        void aSideFaceSpreadsTheAreaUpright() {
            ChannelAim aim = new ChannelAim(new Vec3(10, 64.5, 0.5), GROUND);
            List<BlockPos> area = aim.areaAround(new BlockPos(10, 64, 0));
            assertEquals(9, area.size());
            assertTrue(area.contains(new BlockPos(10, 65, 1)));
            assertTrue(area.contains(new BlockPos(10, 63, -1)));
        }
    }

    @Nested
    class AimedBlock {

        @Test
        void westFaceAimReadsTheBlockBehindTheFace() {
            ChannelAim aim = new ChannelAim(new Vec3(10, 64.5, 0.5), GROUND);

            assertEquals(new BlockPos(10, 64, 0), aim.aimedBlock(new Vec3(8, 65.6, 0.5)));
        }

        @Test
        void topFaceAimFromAboveReadsTheBlockUnderTheFace() {
            ChannelAim aim = new ChannelAim(new Vec3(10.5, 64, 0.5), GROUND);

            assertEquals(new BlockPos(10, 63, 0), aim.aimedBlock(new Vec3(8.5, 65.6, 0.5)));
        }
    }
}
