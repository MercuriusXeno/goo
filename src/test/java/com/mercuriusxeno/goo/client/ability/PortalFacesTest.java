package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.quantum.Gate;
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
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import java.util.List;
import java.util.function.Function;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
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
    /** A one by two face's sixteen by thirty-two texels and a halo's eight more each side. */
    private static final int[] THIRTY_TWO_BY_FORTY_EIGHT = {32, 48};
    /** A wall portal on the north face of (0, 64, 0), its frame (0, 64, 0) to (1, 66, 1). */
    private static final Portal WALL_PORTAL = new Portal(GlobalPos.of(Level.OVERWORLD, new BlockPos(0, 64, 0)),
            Direction.NORTH, Direction.UP);
    /** The portal it links to, the entry of the pair. */
    private static final Portal OTHER_PORTAL = new Portal(GlobalPos.of(Level.OVERWORLD, new BlockPos(9, 64, 0)),
            Direction.NORTH, Direction.UP);
    /** A drawn gate's box as its blocks stand, grown full open. */
    private static final Function<PortalFaces.Drawn, AABB> BLOCKS_OF = drawn -> drawn.gate().blocks();

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
        /** The frame's corners on x, half a block out each side. */
        private static final float[] XS = {-0.5F, 1.5F};
        /** The frame's corners on y, half a block out each end. */
        private static final float[] YS = {63.5F, 66.5F};
        /** The north face's plane, lifted off the frame toward the open side. */
        private static final float FACE_Z = -0.01F;

        @Test
        void aWaitingPortalsHaloReachesTheMarginPastItsFrameInBlue() {
            int rgb = PortalFaces.colorOf(WALL, List.of());

            assertHaloAround(rgb, ARGB.color(PortalFaces.CLEAN_EDGE, PortalFaces.BLUE_RGB));
        }

        @Test
        void aLinkedExitsHaloReachesTheMarginPastItsFrameInOrange() {
            int rgb = PortalFaces.colorOf(WALL, List.of(new QuantumPortals.Link(ENTRY, WALL)));

            assertHaloAround(rgb, ARGB.color(PortalFaces.CLEAN_EDGE, PortalFaces.ORANGE_RGB));
        }

        private void assertHaloAround(int rgb, int color) {
            VertexConsumer consumer = mock(VertexConsumer.class, Answers.RETURNS_SELF);
            PoseStack.Pose pose = new PoseStack().last();
            Gate frame = new Gate(Level.OVERWORLD, WALL.blocks(), WALL.face(), new Vec3(0, 1, 0));

            PortalFaces.emitHalos(pose, consumer, List.of(new PortalFaces.Drawn(frame, rgb, false)), BLOCKS_OF);

            for (float x : XS) {
                for (float y : YS) {
                    verify(consumer).addVertex(pose, x, y, FACE_Z);
                }
            }
            verify(consumer, times(CORNERS)).setColor(color);
            verify(consumer, times(CORNERS)).setUv2(THIRTY_TWO_BY_FORTY_EIGHT[0], THIRTY_TWO_BY_FORTY_EIGHT[1]);
        }
    }

    /**
     * Beside its surface glow, a placed portal wears a tornado: a funnel that
     * starts at its rim on the face and rises off the wall along the face's
     * normal to the named height, in the portal's own color, its texel grid
     * counting sixteen a block around the rim and up the funnel
     * (decision tornadic-halo-spirals-out-from-the-rim).
     */
    @Nested
    class Tornado {

        /** A wall portal on the north face of (0, 64, 0), its frame (0, 64, 0) to (1, 66, 1). */
        private static final Gate FRAME = new Gate(Level.OVERWORLD, new AABB(0, 64, 0, 1, 66, 1), Direction.NORTH,
                new Vec3(0, 1, 0));
        /** The north face's plane, lifted off the frame toward the open side. */
        private static final float FACE_Z = -0.01F;
        /** How far a vertex may stand from where the test reckons it, float rounding only. */
        private static final float NEAR = 1.0e-4F;
        /** The rim's 4.84 blocks around a one by two oval and the funnel's one block up, at sixteen a block. */
        private static final int[] AROUND_BY_UP = {78, 16};

        @Test
        void aWaitingPortalsTornadoRisesFromItsRimToTheNamedHeightInBlue() {
            assertTornadoOff(PortalFaces.colorOf(WALL_PORTAL, List.of()), PortalFaces.BLUE_RGB);
        }

        @Test
        void aLinkedExitsTornadoRisesFromItsRimToTheNamedHeightInOrange() {
            assertTornadoOff(PortalFaces.colorOf(WALL_PORTAL, List.of(new QuantumPortals.Link(OTHER_PORTAL,
                    WALL_PORTAL))), PortalFaces.ORANGE_RGB);
        }

        private void assertTornadoOff(int rgb, int expectedRgb) {
            VertexConsumer consumer = mock(VertexConsumer.class, Answers.RETURNS_SELF);
            PoseStack.Pose pose = new PoseStack().last();
            ArgumentCaptor<Float> xs = ArgumentCaptor.forClass(Float.class);
            ArgumentCaptor<Float> ys = ArgumentCaptor.forClass(Float.class);
            ArgumentCaptor<Float> zs = ArgumentCaptor.forClass(Float.class);

            PortalFaces.emitTornadoes(pose, consumer, List.of(new PortalFaces.Drawn(FRAME, rgb, false)), BLOCKS_OF);

            verify(consumer, atLeastOnce()).addVertex(eq(pose), xs.capture(), ys.capture(), zs.capture());
            int vertices = zs.getAllValues().size();
            float nearest = zs.getAllValues().stream().max(Float::compare).orElseThrow();
            float farthest = zs.getAllValues().stream().min(Float::compare).orElseThrow();
            assertEquals(FACE_Z, nearest, NEAR, "The tornado should start on the face");
            assertEquals(FACE_Z - (float) PortalFaces.TORNADO_HEIGHT, farthest, NEAR,
                    "The tornado should rise off the wall to the named height");
            List<Float> atTheFace = new java.util.ArrayList<>();
            for (int i = 0; i < vertices; i++) {
                if (Math.abs(zs.getAllValues().get(i) - FACE_Z) < NEAR) {
                    atTheFace.add(ovalRadius(xs.getAllValues().get(i), ys.getAllValues().get(i)));
                }
            }
            assertTrue(!atTheFace.isEmpty() && atTheFace.stream().allMatch(radius -> Math.abs(radius - 1F) < NEAR),
                    "Every vertex on the face should stand on the rim's oval, stands at " + atTheFace);
            verify(consumer, times(vertices)).setColor(ARGB.color(PortalFaces.CLEAN_EDGE, expectedRgb));
            verify(consumer, times(vertices)).setUv2(AROUND_BY_UP[0], AROUND_BY_UP[1]);
        }

        /** How far out a point on the north face stands on the frame's oval, one on its rim. */
        private float ovalRadius(float x, float y) {
            return (float) Math.hypot((x - 0.5F) / 0.5F, (y - 65F) / 1F);
        }
    }

    /**
     * Each of a wormhole's two mouths wears the portals' halo, the same
     * margin past its own box, in the mouth's own color, its edge marked
     * torn so the halo starts at the tear
     * (decision hole-mouths-wear-the-portal-halo).
     */
    @Nested
    class MouthHalo {

        /** A three by three mouth on the north face of (0, 64, 0) to (3, 67, 1). */
        private static final Gate THREE_BY_THREE_MOUTH = new Gate(Level.OVERWORLD, new AABB(0, 64, 0, 3, 67, 1),
                Direction.NORTH, new Vec3(0, 1, 0));
        /** A one by two mouth on the floor of (5, 63, 0) to (6, 64, 2). */
        private static final Gate ONE_BY_TWO_MOUTH = new Gate(Level.OVERWORLD, new AABB(5, 63, 0, 6, 64, 2),
                Direction.UP, new Vec3(0, 0, 1));

        @Test
        void aThreeByThreeMouthsHaloReachesTheMarginPastItsBoxInItsColor() {
            VertexConsumer consumer = mock(VertexConsumer.class, Answers.RETURNS_SELF);
            PoseStack.Pose pose = new PoseStack().last();

            PortalFaces.emitHalos(pose, consumer,
                    List.of(new PortalFaces.Drawn(THREE_BY_THREE_MOUTH, PortalFaces.BLUE_RGB, true)), BLOCKS_OF);

            for (float x : new float[] {-0.5F, 3.5F}) {
                for (float y : new float[] {63.5F, 67.5F}) {
                    verify(consumer).addVertex(pose, x, y, -0.01F);
                }
            }
            verify(consumer, times(CORNERS)).setColor(ARGB.color(PortalFaces.TORN_EDGE, PortalFaces.BLUE_RGB));
            verify(consumer, times(CORNERS)).setUv2(64, 64);
        }

        @Test
        void aOneByTwoMouthsHaloReachesTheMarginPastItsBoxInItsColor() {
            VertexConsumer consumer = mock(VertexConsumer.class, Answers.RETURNS_SELF);
            PoseStack.Pose pose = new PoseStack().last();

            PortalFaces.emitHalos(pose, consumer,
                    List.of(new PortalFaces.Drawn(ONE_BY_TWO_MOUTH, PortalFaces.ORANGE_RGB, true)), BLOCKS_OF);

            for (float x : new float[] {4.5F, 6.5F}) {
                for (float z : new float[] {-0.5F, 2.5F}) {
                    verify(consumer).addVertex(pose, x, 64.01F, z);
                }
            }
            verify(consumer, times(CORNERS)).setColor(ARGB.color(PortalFaces.TORN_EDGE, PortalFaces.ORANGE_RGB));
            verify(consumer, times(CORNERS)).setUv2(THIRTY_TWO_BY_FORTY_EIGHT[0], THIRTY_TWO_BY_FORTY_EIGHT[1]);
        }
    }
}
