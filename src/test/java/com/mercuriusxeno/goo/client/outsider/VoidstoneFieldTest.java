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
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the voidstone's field on the floor, through a model of voidstone.fsh:
 * it wraps at the room's width east to west and north to south, so a copy
 * meets the room with no seam (decision voidstone-starfield-tiles-in-either-direction),
 * a block of the room shows one look in the room and in every copy of it,
 * and the block the boundary cuts holds a star in fewer of its texels than a
 * block wholly inside (decision room-edge-draws-a-sparser-starfield).
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
            return VoidstoneFieldModel.roomFace(x, z, FLOOR_Y, CENTER_X + 0.3, CENTER_Z + 0.7, room, false, ticks)
                    .glow();
        }
    }

    @Nested
    class TheEdgeDrawsSparseStars {

        private static final double PARTWAY = 2.3;
        private static final int TEXELS = 16;

        @Test
        void theEdgesStarShareSitsAboveNoneAndBelowTheVoidstones() {
            double edge = constant("EDGE_STARS");
            double voidstone = constant("BRIGHT_STARS") + constant("DIM_STARS");

            assertTrue(edge > 0, "the edge should hold stars");
            assertTrue(edge < voidstone, "the edge's star share " + edge + " should sit below the voidstone's "
                    + voidstone);
        }

        @Test
        void aCutBlockHoldsAStarInFewerOfItsTexelsThanABlockWhollyInside() {
            BlockPos center = OutsiderRooms.voidstoneOf(6);
            RoomExtent.Bounds bounds = new RoomExtent.Bounds(center.getX(), center.getZ(), PARTWAY);
            Matrix4f room = VoidstoneRoom.matrixOf(Optional.of(bounds));
            Map<VoidstoneStars.Density, int[]> drawnAndStarred = new EnumMap<>(VoidstoneStars.Density.class);
            for (VoidstoneStars.Column column : VoidstoneStars.floorColumns(bounds)) {
                int[] counts = drawnAndStarred.computeIfAbsent(column.density(), density -> new int[2]);
                for (int texelX = 0; texelX < TEXELS; texelX++) {
                    for (int texelZ = 0; texelZ < TEXELS; texelZ++) {
                        VoidstoneFieldModel.Look look = VoidstoneFieldModel.roomFace(
                                column.x() + (texelX + 0.5) / TEXELS, column.z() + (texelZ + 0.5) / TEXELS, FLOOR_Y,
                                center.getX() + 0.3, center.getZ() + 0.7, room,
                                column.density() == VoidstoneStars.Density.SPARSE, 0);
                        if (look.drawn()) {
                            counts[0]++;
                            counts[1] += look.star() > 0 ? 1 : 0;
                        }
                    }
                }
            }
            int[] cut = drawnAndStarred.getOrDefault(VoidstoneStars.Density.SPARSE, new int[2]);
            int[] whole = drawnAndStarred.getOrDefault(VoidstoneStars.Density.FULL, new int[2]);

            assertTrue(cut[1] > 0, "the cut blocks should hold a star, of " + cut[0] + " texels drawn");
            assertTrue((double) cut[1] / cut[0] < (double) whole[1] / whole[0], "the cut blocks hold a star in "
                    + cut[1] + " of " + cut[0] + " texels, the whole blocks in " + whole[1] + " of " + whole[0]);
        }

        private static double constant(String name) {
            Matcher value = Pattern.compile("const\\s+float\\s+" + name + "\\s*=\\s*([0-9.]+)\\s*;")
                    .matcher(SkyResources.text(VOID_STARS));
            assertTrue(value.find(), VOID_STARS + " should define " + name);
            return Double.parseDouble(value.group(1));
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
                                cameraZ, room, false, ticks);
                        starred += inRoom.star() > 0 ? 1 : 0;
                        for (RoomCopies.Offset offset : offsets) {
                            VoidstoneFieldModel.Look inCopy = VoidstoneFieldModel.copyFace(x + offset.east(),
                                    z + offset.south(), FLOOR_Y, offset, cameraX, cameraZ, room, false, ticks);
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
