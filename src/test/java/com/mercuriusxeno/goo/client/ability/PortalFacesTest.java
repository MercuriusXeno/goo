package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.quantum.Portal;
import com.mercuriusxeno.goo.ability.quantum.QuantumPortals;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * A gate's face draws on a grid of sixteen texels a block fixed to the face,
 * as a block's texture does, and every vertex of its oval carries that grid's
 * count to the shader (decision portals-and-wormhole-draw-sixteen-texels-a-block).
 */
class PortalFacesTest {

    /** A wall portal one block wide and two tall, on the north face. */
    private static final AABB ONE_BY_TWO_WALL = new AABB(0, 64, 0, 1, 66, 1);
    /** A floor portal one block across and two along z. */
    private static final AABB ONE_BY_TWO_FLOOR = new AABB(0, 64, 0, 1, 65, 2);
    /** A three by three opening. */
    private static final AABB THREE_BY_THREE = new AABB(0, 64, 0, 3, 67, 3);
    private static final int[] SIXTEEN_BY_THIRTY_TWO = {16, 32};
    private static final int[] FORTY_EIGHT_SQUARE = {48, 48};
    private static final int CORNERS = 4;
    private static final int OPAQUE = 255;

    @Test
    void aOneByTwoFaceRunsSixteenByThirtyTwoTexelsOnAWallAFloorAndACeiling() {
        assertArrayEquals(SIXTEEN_BY_THIRTY_TWO, PortalFaces.texelsAcross(ONE_BY_TWO_WALL, Direction.NORTH),
                "A wall face one wide and two tall");
        assertArrayEquals(SIXTEEN_BY_THIRTY_TWO, PortalFaces.texelsAcross(ONE_BY_TWO_FLOOR, Direction.UP),
                "A floor face one across and two along");
        assertArrayEquals(SIXTEEN_BY_THIRTY_TWO, PortalFaces.texelsAcross(ONE_BY_TWO_FLOOR, Direction.DOWN),
                "A ceiling face one across and two along");
    }

    @Test
    void aThreeByThreeFaceRunsFortyEightTexelsEachWayOnAWallAFloorAndACeiling() {
        for (Direction side : new Direction[] {Direction.EAST, Direction.UP, Direction.DOWN}) {
            assertArrayEquals(FORTY_EIGHT_SQUARE, PortalFaces.texelsAcross(THREE_BY_THREE, side),
                    "A three by three face on its " + side + " side");
        }
    }

    /**
     * A hole's mouth draws through the same oval, each corner carrying the
     * count of its own box: a 3x3 mouth 48 by 48, a 1x2 mouth 16 by 32 and a
     * 1x1 mouth 16 by 16.
     */
    @Test
    void everyHolesMouthCarriesTheTexelCountOfItsOwnBox() {
        for (Object[] mouth : new Object[][] {{THREE_BY_THREE, 48, 48}, {ONE_BY_TWO_WALL, 16, 32},
                {new AABB(0, 64, 0, 1, 65, 1), 16, 16}}) {
            VertexConsumer consumer = mock(VertexConsumer.class, Answers.RETURNS_SELF);

            PortalFaces.emitOval(new PoseStack().last(), consumer, (AABB) mouth[0], Direction.NORTH, -1);

            verify(consumer, times(CORNERS)).setUv2((int) mouth[1], (int) mouth[2]);
        }
    }

    @Test
    void everyCornerOfAnOvalCarriesItsFacesTexelCount() {
        VertexConsumer consumer = mock(VertexConsumer.class, Answers.RETURNS_SELF);

        PortalFaces.emitOval(new PoseStack().last(), consumer, ONE_BY_TWO_WALL, Direction.NORTH, -1);

        verify(consumer, times(CORNERS)).setUv2(SIXTEEN_BY_THIRTY_TWO[0], SIXTEEN_BY_THIRTY_TWO[1]);
    }

    /**
     * A placed portal's halo reaches half a block past its frame on each way
     * across its face, in the portal's own color, its grid counting sixteen
     * texels a block over the frame and that margin
     * (decision portal-halo-glows-additively-onto-the-wall).
     */
    @Nested
    class Halo {

        /** A wall portal on the north face of (0, 64, 0), its frame (0, 64, 0) to (1, 66, 1). */
        private static final Portal WALL = new Portal(GlobalPos.of(Level.OVERWORLD, new BlockPos(0, 64, 0)),
                Direction.NORTH, Direction.UP);
        /** The portal it links to, the entry of the pair. */
        private static final Portal ENTRY = new Portal(GlobalPos.of(Level.OVERWORLD, new BlockPos(9, 64, 0)),
                Direction.NORTH, Direction.UP);
        /** The frame's sixteen by thirty-two texels and eight more each side. */
        private static final int[] THIRTY_TWO_BY_FORTY_EIGHT = {32, 48};
        /** The frame's corners on x, half a block out each side. */
        private static final float[] XS = {-0.5F, 1.5F};
        /** The frame's corners on y, half a block out each end. */
        private static final float[] YS = {63.5F, 66.5F};
        /** The north face's plane, lifted off the frame toward the open side. */
        private static final float FACE_Z = -0.01F;

        @Test
        void aWaitingPortalsHaloReachesTheMarginPastItsFrameInBlue() {
            int rgb = PortalFaces.colorOf(WALL, List.of());

            assertHaloAround(rgb, ARGB.color(PortalFacesTest.OPAQUE, PortalFaces.BLUE_RGB));
        }

        @Test
        void aLinkedExitsHaloReachesTheMarginPastItsFrameInOrange() {
            int rgb = PortalFaces.colorOf(WALL, List.of(new QuantumPortals.Link(ENTRY, WALL)));

            assertHaloAround(rgb, ARGB.color(PortalFacesTest.OPAQUE, PortalFaces.ORANGE_RGB));
        }

        private void assertHaloAround(int rgb, int color) {
            VertexConsumer consumer = mock(VertexConsumer.class, Answers.RETURNS_SELF);
            PoseStack.Pose pose = new PoseStack().last();

            PortalFaces.emitHalo(pose, consumer, WALL.blocks(), WALL.face(), rgb);

            for (float x : XS) {
                for (float y : YS) {
                    verify(consumer).addVertex(pose, x, y, FACE_Z);
                }
            }
            verify(consumer, times(CORNERS)).setColor(color);
            verify(consumer, times(CORNERS)).setUv2(THIRTY_TWO_BY_FORTY_EIGHT[0], THIRTY_TWO_BY_FORTY_EIGHT[1]);
        }
    }
}
