package com.mercuriusxeno.goo.client.outsider;

import com.mercuriusxeno.goo.ability.outsider.OutsiderRooms;
import com.mercuriusxeno.goo.ability.outsider.RoomEdges;
import com.mercuriusxeno.goo.ability.outsider.RoomExtent;
import com.mercuriusxeno.goo.ability.quantum.GatePassage;
import com.mercuriusxeno.goo.client.ability.ClientRoomEdges;
import com.mercuriusxeno.goo.network.RoomEdgesSyncPayload;
import net.minecraft.core.BlockPos;
import org.joml.Matrix4f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the voidstone's field on the floor, through a model of voidstone.fsh:
 * it wraps at the room's width east to west and north to south, so a copy
 * meets the room with no seam (decision voidstone-starfield-tiles-in-either-direction),
 * a block of the room shows one look in the room and in every copy of it,
 * and every star, the block the boundary cuts included, holds its texel and
 * brightness as the room grows (decision room-edge-draws-a-sparser-starfield).
 * The model holds the shader's expressions it mirrors, and fails where the
 * shader drifts from them.
 */
class VoidstoneFieldTest {

    private static final String VOIDSTONE_SHADER = "assets/goo/shaders/core/voidstone.fsh";
    private static final String VOID_STARS = "assets/goo/shaders/include/void_stars.glsl";
    private static final int FLOOR_Y = OutsiderRooms.ROOM_FLOOR_Y;
    /** How closely two glows the wrap makes equal agree, allowing the float the shader places a fragment in. */
    private static final double GLOW_TOLERANCE = 1.0E-4;
    /** Steps along a plane: an odd fraction of a block, so samples fall at every place within a texel. */
    private static final double ALONG_STEP = 1.0 / 7.0;
    private static final double[] CLOCKS = {0, 1_234.5, 20_000};

    @Nested
    class WrapsAtTheRoomsWidth {

        /** Far out and on both sides of zero, as rooms stand. */
        private static final int CENTER_X = 1_000_003;
        private static final int CENTER_Z = -2_000_005;

        @ParameterizedTest(name = "extent {0}")
        @ValueSource(doubles = {2.5, 2.3})
        void theEastPlaneGlowsAsTheWestPlaneDoes(double extent) {
            RoomExtent.Planes planes = planesOf(extent);
            Matrix4f room = VoidstoneRoom.matrixOf(Optional.of(new RoomExtent.Bounds(CENTER_X, CENTER_Z, extent)));
            double low = Double.MAX_VALUE;
            double high = -Double.MAX_VALUE;
            for (double ticks : CLOCKS) {
                for (double z = planes.north(); z <= planes.south(); z += ALONG_STEP) {
                    double east = glowAt(planes.east(), z, room, ticks);
                    double west = glowAt(planes.west(), z, room, ticks);
                    assertEquals(west, east, GLOW_TOLERANCE,
                            "the east plane at z " + z + " should glow as the west plane, clock " + ticks);
                    low = Math.min(low, east);
                    high = Math.max(high, east);
                }
            }
            assertTrue(high - low > GLOW_TOLERANCE, "the glow along the plane should vary, not stand flat");
        }

        @ParameterizedTest(name = "extent {0}")
        @ValueSource(doubles = {2.5, 2.3})
        void theSouthPlaneGlowsAsTheNorthPlaneDoes(double extent) {
            RoomExtent.Planes planes = planesOf(extent);
            Matrix4f room = VoidstoneRoom.matrixOf(Optional.of(new RoomExtent.Bounds(CENTER_X, CENTER_Z, extent)));
            double low = Double.MAX_VALUE;
            double high = -Double.MAX_VALUE;
            for (double ticks : CLOCKS) {
                for (double x = planes.west(); x <= planes.east(); x += ALONG_STEP) {
                    double south = glowAt(x, planes.south(), room, ticks);
                    double north = glowAt(x, planes.north(), room, ticks);
                    assertEquals(north, south, GLOW_TOLERANCE,
                            "the south plane at x " + x + " should glow as the north plane, clock " + ticks);
                    low = Math.min(low, south);
                    high = Math.max(high, south);
                }
            }
            assertTrue(high - low > GLOW_TOLERANCE, "the glow along the plane should vary, not stand flat");
        }

        @Test
        void theShaderHoldsEveryExpressionTheModelMirrors() {
            String shader = SkyResources.text(VOIDSTONE_SHADER) + SkyResources.text(VOID_STARS);
            for (String expression : VoidstoneFieldModel.MIRRORED) {
                assertTrue(shader.contains(expression), VOIDSTONE_SHADER + " should hold " + expression);
            }
        }

        private static RoomExtent.Planes planesOf(double extent) {
            return RoomExtent.planesAround(new BlockPos(CENTER_X, FLOOR_Y, CENTER_Z), extent);
        }

        private static double glowAt(double x, double z, Matrix4f room, double ticks) {
            return VoidstoneFieldModel.roomFace(x, z, FLOOR_Y, CENTER_X + 0.3, CENTER_Z + 0.7, room, ticks)
                    .glow();
        }
    }

    @Nested
    class StarsHoldAsTheRoomGrows {

        /** The room cut three tenths into the ring two out, then past that ring, then into the ring three out. */
        private static final double[] EXTENTS = {2.3, 2.6, 3.1};
        private static final int TEXELS = 16;

        @Test
        void everyStarKeepsItsTexelAndBrightnessTheEdgeBlockIncluded() {
            BlockPos center = OutsiderRooms.voidstoneOf(6);
            RoomExtent.Bounds first = new RoomExtent.Bounds(center.getX(), center.getZ(), EXTENTS[0]);
            int edgeStars = 0;
            for (VoidstoneStars.Column column : VoidstoneStars.floorColumns(first)) {
                boolean edge = first.cutsBlock(column.x(), column.z());
                for (int texelX = 0; texelX < TEXELS; texelX++) {
                    for (int texelZ = 0; texelZ < TEXELS; texelZ++) {
                        double x = column.x() + (texelX + 0.5) / TEXELS;
                        double z = column.z() + (texelZ + 0.5) / TEXELS;
                        VoidstoneFieldModel.Look before = lookAt(x, z, center, EXTENTS[0]);
                        if (!before.drawn()) {
                            continue;
                        }
                        edgeStars += edge && before.star() > 0 ? 1 : 0;
                        for (double extent : EXTENTS) {
                            assertEquals(before.star(), lookAt(x, z, center, extent).star(),
                                    "the star at " + x + ", " + z + " should hold at extent " + extent);
                        }
                    }
                }
            }
            assertTrue(edgeStars > 0, "the edge blocks should hold stars");
        }

        private static VoidstoneFieldModel.Look lookAt(double x, double z, BlockPos center, double extent) {
            Matrix4f room = VoidstoneRoom.matrixOf(
                    Optional.of(new RoomExtent.Bounds(center.getX(), center.getZ(), extent)));
            return VoidstoneFieldModel.roomFace(x, z, FLOOR_Y, center.getX() + 0.3, center.getZ() + 0.7, room,
                    CLOCKS[1]);
        }
    }

    @Nested
    class TheRoomAndItsCopiesDrawOneField {

        private static final BlockPos CENTER = OutsiderRooms.voidstoneOf(6);
        private static final double PARTWAY = 2.3;
        private static final double REACH = 64;
        /** A block wholly inside the room, in the band the field fades across toward the east and south planes. */
        private static final int BLOCK_EAST = 1;
        private static final int BLOCK_SOUTH = 1;
        private static final int TEXELS = 16;

        @AfterEach
        void leaveTheRoom() {
            ClientRoomEdges.take(new RoomEdgesSyncPayload(List.of()));
            GatePassage.setClientPairs(List.of());
        }

        @Test
        void aRoomBlockShowsOneLookInTheRoomAndInEveryCopy() {
            ClientRoomEdges.take(new RoomEdgesSyncPayload(RoomEdges.around(CENTER, PARTWAY).pairs()));
            RoomExtent.Bounds bounds = ClientRoomEdges.bounds().orElseThrow();
            Matrix4f room = VoidstoneRoom.current();
            double cameraX = CENTER.getX() + 0.3;
            double cameraZ = CENTER.getZ() + 0.7;
            List<RoomCopies.Offset> offsets = RoomCopies.offsets(bounds, REACH);
            assertTrue(!offsets.isEmpty(), "the room should stand copies around it");
            int starred = 0;
            for (double ticks : CLOCKS) {
                for (int texelX = 0; texelX < TEXELS; texelX++) {
                    for (int texelZ = 0; texelZ < TEXELS; texelZ++) {
                        double x = CENTER.getX() + BLOCK_EAST + (texelX + 0.5) / TEXELS;
                        double z = CENTER.getZ() + BLOCK_SOUTH + (texelZ + 0.5) / TEXELS;
                        VoidstoneFieldModel.Look inRoom = VoidstoneFieldModel.roomFace(x, z, FLOOR_Y, cameraX,
                                cameraZ, room, ticks);
                        starred += inRoom.star() > 0 ? 1 : 0;
                        for (RoomCopies.Offset offset : offsets) {
                            VoidstoneFieldModel.Look inCopy = VoidstoneFieldModel.copyFace(x + offset.east(),
                                    z + offset.south(), FLOOR_Y, offset, cameraX, cameraZ, room, ticks);
                            assertEquals(inRoom.glow(), inCopy.glow(), GLOW_TOLERANCE,
                                    "the glow, and with it the base color, at " + x + ", " + z + " in copy " + offset);
                            assertEquals(inRoom.star(), inCopy.star(), GLOW_TOLERANCE,
                                    "the star and its brightness at " + x + ", " + z + " in copy " + offset);
                        }
                    }
                }
            }
            assertTrue(starred > 0, "the block should hold a star somewhere in the sweep");
        }
    }
}
