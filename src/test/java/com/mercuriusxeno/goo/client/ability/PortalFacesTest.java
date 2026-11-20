package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.quantum.PortalColor;
import com.mercuriusxeno.goo.ability.quantum.Gate;
import com.mercuriusxeno.goo.ability.quantum.GatePassage;
import com.mercuriusxeno.goo.ability.quantum.Portal;
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
            Direction.NORTH, Direction.UP, PortalColor.BLUE);
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
     * A tunnel's mouth draws through the same oval, each corner carrying the
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
     * A gate draws its view inside its oval where one drew, else its idle
     * swirl, which inside a view's own pass only an inert gate shows.
     */
    @Nested
    class ByTheViewItDrew {

        @Test
        void aViewedGateDrawsTheOvalView() {
            assertEquals(PortalFaces.Face.OVAL_VIEW, PortalFaces.faceOf(true, false, false));
        }

        @Test
        void aGateWithNoViewSwirls() {
            assertEquals(PortalFaces.Face.SWIRL, PortalFaces.faceOf(false, false, false));
        }

        @Test
        void anInertGateInAViewsPassSwirlsWhereALinkedOneDrawsNothing() {
            assertEquals(PortalFaces.Face.SWIRL, PortalFaces.faceOf(false, true, true));
            assertEquals(PortalFaces.Face.NONE, PortalFaces.faceOf(false, true, false));
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
                Direction.NORTH, Direction.UP, PortalColor.BLUE);
        /** The frame's corners on x, half a block out each side. */
        private static final float[] XS = {-0.5F, 1.5F};
        /** The frame's corners on y, half a block out each end. */
        private static final float[] YS = {63.5F, 66.5F};
        /** The north face's plane, lifted off the frame toward the open side. */
        private static final float FACE_Z = -0.01F;

        @Test
        void aBluePortalsHaloReachesTheMarginPastItsFrameInBlue() {
            int rgb = PortalFaces.colorOf(WALL);

            assertHaloAround(rgb, ARGB.opaque(PortalFaces.BLUE_RGB));
        }

        @Test
        void anOrangePortalsHaloReachesTheMarginPastItsFrameInOrange() {
            int rgb = PortalFaces.colorOf(WALL.withColor(PortalColor.ORANGE));

            assertHaloAround(rgb, ARGB.opaque(PortalFaces.ORANGE_RGB));
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
     * Beside its surface glow, a placed portal wears a swirl band: an arch that
     * lands on the face inside and outside its rim and peaks off the wall
     * right over the rim at the named depth, in the portal's own color, its
     * texels counted sixteen a block around the rim and across the arch
     * (decision tornadic-halo-spirals-out-from-the-rim).
     */
    @Nested
    class SwirlBand {

        /** A wall portal on the north face of (0, 64, 0), its frame (0, 64, 0) to (1, 66, 1). */
        private static final Gate FRAME = new Gate(Level.OVERWORLD, new AABB(0, 64, 0, 1, 66, 1), Direction.NORTH,
                new Vec3(0, 1, 0));
        /** The north face's plane, lifted off the frame toward the open side. */
        private static final float FACE_Z = -0.01F;
        /** How far a vertex may stand from where the test reckons it, float rounding only. */
        private static final float NEAR = 1.0e-4F;
        /** The rim's 4.84 blocks around a one by two oval and the arch's two texels across, at sixteen a block. */
        private static final int[] AROUND_BY_UP = {78, 2};
        /** The band's line on the frame's oval: half a texel inside its edge each way. */
        private static final double[] LINE = {0.5 - PortalFaces.BAND_INSET, 1 - PortalFaces.BAND_INSET};

        /**
         * The band's sleeve, the half behind the face, is a ring at the band's
         * line running back into the portal the named sleeve depth, in the
         * color it is given.
         */
        @Test
        void aPortalsSleeveRunsFromItsFaceBackIntoItOnTheBandsLine() {
            VertexConsumer consumer = mock(VertexConsumer.class, Answers.RETURNS_SELF);
            PoseStack.Pose pose = new PoseStack().last();
            ArgumentCaptor<Float> xs = ArgumentCaptor.forClass(Float.class);
            ArgumentCaptor<Float> ys = ArgumentCaptor.forClass(Float.class);
            ArgumentCaptor<Float> zs = ArgumentCaptor.forClass(Float.class);

            PortalFaces.emitSleeves(pose, consumer, List.of(new PortalFaces.Drawn(FRAME, PortalFaces.ORANGE_RGB,
                    false)), BLOCKS_OF);

            verify(consumer, atLeastOnce()).addVertex(eq(pose), xs.capture(), ys.capture(), zs.capture());
            int vertices = zs.getAllValues().size();
            assertEquals(FACE_Z, zs.getAllValues().stream().max(Float::compare).orElseThrow(), NEAR,
                    "The sleeve should start on the face");
            assertEquals(FACE_Z - (float) PortalFaces.BAND_SLEEVE, zs.getAllValues().stream().min(Float::compare)
                    .orElseThrow(), NEAR, "The sleeve should run the named depth back from the face");
            for (int i = 0; i < vertices; i++) {
                assertTrue(onOval(xs.getAllValues().get(i) - 0.5F, ys.getAllValues().get(i) - 65F, LINE[0],
                        LINE[1]), "Every vertex of the sleeve should stand on the band's line");
            }
            verify(consumer, times(vertices)).setColor(ARGB.opaque(PortalFaces.ORANGE_RGB));
        }

        @Test
        void aBluePortalsSwirlBandArchesOverItsRimToTheNamedDepthInBlue() {
            assertSwirlBandOff(PortalFaces.colorOf(WALL_PORTAL), PortalFaces.BLUE_RGB);
        }

        @Test
        void anOrangePortalsSwirlBandArchesOverItsRimToTheNamedDepthInOrange() {
            assertSwirlBandOff(PortalFaces.colorOf(WALL_PORTAL.withColor(PortalColor.ORANGE)), PortalFaces.ORANGE_RGB);
        }

        private void assertSwirlBandOff(int rgb, int expectedRgb) {
            VertexConsumer consumer = mock(VertexConsumer.class, Answers.RETURNS_SELF);
            PoseStack.Pose pose = new PoseStack().last();
            ArgumentCaptor<Float> xs = ArgumentCaptor.forClass(Float.class);
            ArgumentCaptor<Float> ys = ArgumentCaptor.forClass(Float.class);
            ArgumentCaptor<Float> zs = ArgumentCaptor.forClass(Float.class);

            PortalFaces.emitSwirlBands(pose, consumer, List.of(new PortalFaces.Drawn(FRAME, rgb, false)), BLOCKS_OF);

            verify(consumer, atLeastOnce()).addVertex(eq(pose), xs.capture(), ys.capture(), zs.capture());
            int vertices = zs.getAllValues().size();
            float nearest = zs.getAllValues().stream().max(Float::compare).orElseThrow();
            float farthest = zs.getAllValues().stream().min(Float::compare).orElseThrow();
            assertEquals(FACE_Z, nearest, NEAR, "The swirl band should land on the face");
            assertEquals(FACE_Z - (float) PortalFaces.BAND_DEPTH, farthest, NEAR,
                    "The swirl band should peak off the wall at the named depth");
            for (int i = 0; i < vertices; i++) {
                float x = xs.getAllValues().get(i);
                float y = ys.getAllValues().get(i);
                float z = zs.getAllValues().get(i);
                if (Math.abs(z - FACE_Z) < NEAR) {
                    assertTrue(onOval(x - 0.5F, y - 65F, LINE[0] - PortalFaces.BAND_INSIDE,
                            LINE[1] - PortalFaces.BAND_INSIDE) || onOval(x - 0.5F, y - 65F,
                            LINE[0] + PortalFaces.BAND_OUTSIDE, LINE[1] + PortalFaces.BAND_OUTSIDE),
                            "A vertex on the face should land the named distance inside or outside the band's line, "
                                    + "lands at " + x + ", " + y);
                } else if (Math.abs(z - farthest) < NEAR) {
                    assertTrue(onOval(x - 0.5F, y - 65F, LINE[0], LINE[1]), "The band's peak should stand on its line, "
                            + "stands at " + x + ", " + y);
                }
            }
            verify(consumer, times(vertices)).setColor(ARGB.opaque(expectedRgb));
            verify(consumer, times(vertices)).setUv2(AROUND_BY_UP[0], AROUND_BY_UP[1]);
        }
    }

    /**
     * @param first  a point's offset from an oval's middle across its first way
     * @param second its offset across the second way
     * @param half   the oval's half size across its first way
     * @param other  its half size across its second way
     * @return whether the point stands on the oval
     */
    private static boolean onOval(float first, float second, double half, double other) {
        return Math.abs(Math.hypot(first / half, second / other) - 1) < 1.0e-3;
    }

    /**
     * Inside a view through a pair, the partner the view looks out of wears
     * no halo or swirl band, so the near portal's view is never ringed in the
     * partner's color, blue showing on the orange one; every other gate the
     * view sees wears its own.
     */
    @Test
    void theViewThroughAPairLeavesOutThePartnersHaloAndBand() {
        Gate blue = new Gate(Level.OVERWORLD, new AABB(0, 64, 0, 1, 66, 1), Direction.NORTH, new Vec3(0, 1, 0));
        Gate orange = new Gate(Level.OVERWORLD, new AABB(9, 64, 0, 10, 66, 1), Direction.NORTH, new Vec3(0, 1, 0));
        Gate seen = new Gate(Level.OVERWORLD, new AABB(20, 64, 0, 21, 66, 1), Direction.NORTH, new Vec3(0, 1, 0));
        List<PortalFaces.Drawn> gates = List.of(new PortalFaces.Drawn(blue, PortalFaces.BLUE_RGB, false),
                new PortalFaces.Drawn(orange, PortalFaces.ORANGE_RGB, false),
                new PortalFaces.Drawn(seen, PortalFaces.BLUE_RGB, false));

        List<Gate> worn = PortalFaces.wornIn(gates, java.util.Optional.of(blue)).stream()
                .map(PortalFaces.Drawn::gate).toList();

        assertEquals(List.of(orange, seen), worn, "Looking out of the blue portal, only the others wear theirs");
        assertEquals(3, PortalFaces.wornIn(gates, java.util.Optional.empty()).size(),
                "Outside a view, every gate wears its own");
    }

    /**
     * The swirl band straddles the portal's face, and its half behind the
     * face runs back into the portal: inside a view through a pair, the
     * partner the view looks out of wears the near portal's sleeve in the
     * near portal's color, so looking into the orange portal its orange
     * swirl recedes into it, never blue. Outside a view, no gate wears one.
     */
    @Test
    void theViewThroughAPairSleevesTheNearPortalsBandBackIntoIt() {
        Gate blue = new Gate(Level.OVERWORLD, new AABB(0, 64, 0, 1, 66, 1), Direction.NORTH, new Vec3(0, 1, 0));
        Gate orange = new Gate(Level.OVERWORLD, new AABB(9, 64, 0, 10, 66, 1), Direction.NORTH, new Vec3(0, 1, 0));
        Gate seen = new Gate(Level.OVERWORLD, new AABB(20, 64, 0, 21, 66, 1), Direction.NORTH, new Vec3(0, 1, 0));
        List<PortalFaces.Drawn> gates = List.of(new PortalFaces.Drawn(blue, PortalFaces.BLUE_RGB, false),
                new PortalFaces.Drawn(orange, PortalFaces.ORANGE_RGB, false),
                new PortalFaces.Drawn(seen, PortalFaces.BLUE_RGB, false));

        List<PortalFaces.Drawn> sleeved = PortalFaces.sleevedIn(gates, java.util.Optional.of(
                new GatePassage.Pair(orange, blue)));

        assertEquals(List.of(new PortalFaces.Drawn(blue, PortalFaces.ORANGE_RGB, false)), sleeved,
                "Looking into the orange portal, the blue partner it looks out of wears the orange sleeve");
        assertEquals(List.of(), PortalFaces.sleevedIn(gates, java.util.Optional.empty()),
                "Outside a view, no gate wears a sleeve");
    }

    /**
     * A Tunnel mouth draws its rim through the portals' drawing, its oval
     * standing two texels inside its blocks each way across the face: its
     * halo, swirl band and sleeve emit what a Portal's on that oval's box do,
     * in its own color
     * (decision tunnel-drops-its-crisp-wiggly-outline-and-looks-like-the-portals).
     */
    @Nested
    class TunnelMouth {

        /** A three by three mouth on the north face of (0, 64, 0) to (3, 67, 1). */
        private static final Gate WALL_MOUTH = new Gate(Level.OVERWORLD, new AABB(0, 64, 0, 3, 67, 1),
                Direction.NORTH, new Vec3(0, 1, 0));
        /** A one by two mouth on the floor of (5, 63, 0) to (6, 64, 2). */
        private static final Gate FLOOR_MOUTH = new Gate(Level.OVERWORLD, new AABB(5, 63, 0, 6, 64, 2),
                Direction.UP, new Vec3(0, 0, 1));

        /** A drawn gate's box as its oval fills it, grown full open. */
        private static final Function<PortalFaces.Drawn, AABB> OVAL_OF = PortalFaces.Drawn::ovalBox;

        @Test
        void aMouthsOvalStandsTwoTexelsInsideItsBlocksEachWayAcrossTheFace() {
            double in = 2.0 / 16;
            assertEquals(new AABB(in, 64 + in, 0, 3 - in, 67 - in, 1),
                    new PortalFaces.Drawn(WALL_MOUTH, PortalFaces.BLUE_RGB, true).ovalBox(), "A wall mouth");
            assertEquals(new AABB(5 + in, 63, in, 6 - in, 64, 2 - in),
                    new PortalFaces.Drawn(FLOOR_MOUTH, PortalFaces.ORANGE_RGB, true).ovalBox(), "A floor mouth");
        }

        /**
         * A Portal's oval stands the mouth inset inside its blocks each way
         * across the face and spans them whole along the normal, the box a
         * Tunnel mouth on the same blocks takes
         * (decision portal-mouths-draw-shy-of-their-edges-like-tunnels).
         */
        @Test
        void aPortalsOvalStandsTwoTexelsInsideItsBlocksAsATunnelMouthsDoes() {
            double in = PortalFaces.MOUTH_INSET;
            assertEquals(2.0 / 16, in, "The mouth inset is two texels");
            assertEquals(new AABB(in, 64 + in, 0, 3 - in, 67 - in, 1),
                    new PortalFaces.Drawn(WALL_MOUTH, PortalFaces.BLUE_RGB, false).ovalBox(), "A wall portal");
            assertEquals(new AABB(5 + in, 63, in, 6 - in, 64, 2 - in),
                    new PortalFaces.Drawn(FLOOR_MOUTH, PortalFaces.ORANGE_RGB, false).ovalBox(), "A floor portal");
            for (Gate gate : List.of(WALL_MOUTH, FLOOR_MOUTH)) {
                assertEquals(new PortalFaces.Drawn(gate, PortalFaces.BLUE_RGB, true).ovalBox(),
                        new PortalFaces.Drawn(gate, PortalFaces.BLUE_RGB, false).ovalBox(),
                        "A portal's oval should be the box a Tunnel mouth on " + gate.blocks() + " takes");
            }
        }

        @Test
        void aWallMouthDrawsItsRimAsAPortalOnItsOvalsBoxDoes() {
            assertDrawsAsAPortal(WALL_MOUTH, PortalFaces.BLUE_RGB);
        }

        @Test
        void aFloorMouthDrawsItsRimAsAPortalOnItsOvalsBoxDoes() {
            assertDrawsAsAPortal(FLOOR_MOUTH, PortalFaces.ORANGE_RGB);
        }

        private void assertDrawsAsAPortal(Gate gate, int rgb) {
            PortalFaces.Drawn mouth = new PortalFaces.Drawn(gate, rgb, true);
            PortalFaces.Drawn portal = new PortalFaces.Drawn(new Gate(gate.dimension(), mouth.ovalBox(), gate.face(),
                    gate.up()), rgb, false);
            PoseStack.Pose pose = new PoseStack().last();
            assertEquals(emitted(pose, portal, BLOCKS_OF), emitted(pose, mouth, OVAL_OF),
                    "A Tunnel mouth's halo, swirl band and sleeve should emit what a Portal's on its oval's box do");
        }

        /** Every call the halo, swirl band and sleeve emitters make for one gate, in order. */
        private List<String> emitted(PoseStack.Pose pose, PortalFaces.Drawn drawn,
                Function<PortalFaces.Drawn, AABB> boxOf) {
            VertexConsumer consumer = mock(VertexConsumer.class, Answers.RETURNS_SELF);
            PortalFaces.emitHalos(pose, consumer, List.of(drawn), boxOf);
            PortalFaces.emitSwirlBands(pose, consumer, List.of(drawn), boxOf);
            PortalFaces.emitSleeves(pose, consumer, List.of(drawn), boxOf);
            return org.mockito.Mockito.mockingDetails(consumer).getInvocations().stream()
                    .map(call -> call.getMethod().getName() + java.util.Arrays.toString(call.getArguments()))
                    .toList();
        }
    }

    /**
     * Each of a wormhole's two mouths wears the portals' halo, the same
     * margin past its own box, in the mouth's own color
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
            verify(consumer, times(CORNERS)).setColor(ARGB.opaque(PortalFaces.BLUE_RGB));
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
            verify(consumer, times(CORNERS)).setColor(ARGB.opaque(PortalFaces.ORANGE_RGB));
            verify(consumer, times(CORNERS)).setUv2(THIRTY_TWO_BY_FORTY_EIGHT[0], THIRTY_TWO_BY_FORTY_EIGHT[1]);
        }
    }
}
