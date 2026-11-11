package com.mercuriusxeno.goo.client.ability;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
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

    /**
     * A room's edge draws its view over its whole rectangle and nothing where
     * it has no view or a view's own pass draws, where an oval gate draws its
     * view inside the oval or swirls
     * (decision outsider-room-wraps-and-repeats-at-the-platform-edge).
     */
    @org.junit.jupiter.api.Nested
    class ByTheGatesMask {

        private static final net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> OVERWORLD =
                net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                        net.minecraft.resources.Identifier.withDefaultNamespace("overworld"));
        private static final com.mercuriusxeno.goo.ability.quantum.Gate OVAL =
                new com.mercuriusxeno.goo.ability.quantum.Gate(OVERWORLD, ONE_BY_TWO_WALL, Direction.NORTH,
                        Direction.UP.getUnitVec3());
        private static final com.mercuriusxeno.goo.ability.quantum.Gate RECTANGLE =
                new com.mercuriusxeno.goo.ability.quantum.Gate(OVERWORLD, ONE_BY_TWO_WALL, Direction.NORTH,
                        Direction.UP.getUnitVec3(), com.mercuriusxeno.goo.ability.quantum.Gate.Mask.RECTANGLE);

        @Test
        void aViewedRectangularGateDrawsTheRectangularView() {
            org.junit.jupiter.api.Assertions.assertEquals(PortalFaces.Face.RECTANGLE_VIEW,
                    PortalFaces.faceOf(RECTANGLE, true, false, false));
        }

        @Test
        void aViewedOvalGateDrawsTheOvalView() {
            org.junit.jupiter.api.Assertions.assertEquals(PortalFaces.Face.OVAL_VIEW,
                    PortalFaces.faceOf(OVAL, true, false, false));
        }

        @Test
        void aRectangularGateWithNoViewDrawsNothingWhereAnOvalSwirls() {
            org.junit.jupiter.api.Assertions.assertEquals(PortalFaces.Face.NONE,
                    PortalFaces.faceOf(RECTANGLE, false, false, false));
            org.junit.jupiter.api.Assertions.assertEquals(PortalFaces.Face.SWIRL,
                    PortalFaces.faceOf(OVAL, false, false, false));
        }

        @Test
        void aRectangularGateInAViewsPassDrawsNothingWhereAnInertOvalSwirls() {
            org.junit.jupiter.api.Assertions.assertEquals(PortalFaces.Face.NONE,
                    PortalFaces.faceOf(RECTANGLE, false, true, true));
            org.junit.jupiter.api.Assertions.assertEquals(PortalFaces.Face.SWIRL,
                    PortalFaces.faceOf(OVAL, false, true, true));
        }
    }

    @Test
    void everyCornerOfAnOvalCarriesItsFacesTexelCount() {
        VertexConsumer consumer = mock(VertexConsumer.class, Answers.RETURNS_SELF);

        PortalFaces.emitOval(new PoseStack().last(), consumer, ONE_BY_TWO_WALL, Direction.NORTH, -1);

        verify(consumer, times(CORNERS)).setUv2(SIXTEEN_BY_THIRTY_TWO[0], SIXTEEN_BY_THIRTY_TWO[1]);
    }
}
