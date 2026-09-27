package com.mercuriusxeno.goo.client.particle;

import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Covers the tap drop's cuboid: every face tiled with square quads whose fronts face out of it. */
class DripCuboidTest {

    private static final float HALF = TapDripParticle.DROP_HALF_SIZE;
    private static final double BOTTOM_Y = 64.3;
    private static final double TOLERANCE = 1e-5;

    /** The falling 2x2x2 cube and the hanging 1x2x2 shape. */
    static Stream<DripCuboid.Extent> dropShapes() {
        return Stream.of(new DripCuboid.Extent(BOTTOM_Y, HALF, 2f * HALF),
                new DripCuboid.Extent(BOTTOM_Y, HALF, HALF),
                new DripCuboid.Extent(BOTTOM_Y, HALF * 0.25f, HALF * 0.25f));
    }

    /** The outward normal of the face a point on the cuboid's surface sits on. */
    private static Vector3f outwardNormal(DripCuboid.Extent cuboid, DripCuboid.Tile tile) {
        double centerY = cuboid.bottomY() + cuboid.height() / 2.0;
        if (Math.abs(tile.y() - cuboid.topY()) < TOLERANCE) {
            return new Vector3f(0, 1, 0);
        }
        if (Math.abs(tile.y() - cuboid.bottomY()) < TOLERANCE) {
            return new Vector3f(0, -1, 0);
        }
        assertTrue(Math.abs(tile.y() - centerY) < cuboid.height() / 2.0, "side tile y inside the cuboid: " + tile);
        if (Math.abs(Math.abs(tile.dx()) - cuboid.halfWidth()) < TOLERANCE) {
            return new Vector3f((float) Math.signum(tile.dx()), 0, 0);
        }
        assertEquals(cuboid.halfWidth(), Math.abs(tile.dz()), TOLERANCE, "tile off every face: " + tile);
        return new Vector3f(0, 0, (float) Math.signum(tile.dz()));
    }

    @ParameterizedTest
    @MethodSource("dropShapes")
    void everyTileFacesOutOfTheFaceItSitsOn(DripCuboid.Extent cuboid) {
        for (DripCuboid.Tile tile : DripCuboid.tiles(cuboid)) {
            Vector3f front = new Vector3f(0, 0, 1).rotate(new Quaternionf(tile.rotation()));
            Vector3f normal = outwardNormal(cuboid, tile);
            assertTrue(front.distance(normal) < TOLERANCE, "tile " + tile + " faces " + front + ", not " + normal);
        }
    }

    @ParameterizedTest
    @MethodSource("dropShapes")
    void everyTileCornerLiesOnTheCuboidsSurface(DripCuboid.Extent cuboid) {
        for (DripCuboid.Tile tile : DripCuboid.tiles(cuboid)) {
            for (float[] corner : new float[][] {{1, 1}, {1, -1}, {-1, 1}, {-1, -1}}) {
                Vector3f at = new Vector3f(corner[0], corner[1], 0).rotate(new Quaternionf(tile.rotation()))
                        .mul(tile.halfSize()).add((float) tile.dx(), 0, (float) tile.dz());
                double y = tile.y() + at.y();
                String where = "tile " + tile + " corner " + at;
                assertTrue(Math.abs(at.x()) <= cuboid.halfWidth() + TOLERANCE, where);
                assertTrue(Math.abs(at.z()) <= cuboid.halfWidth() + TOLERANCE, where);
                assertTrue(y >= cuboid.bottomY() - TOLERANCE && y <= cuboid.topY() + TOLERANCE, where);
            }
        }
    }

    @ParameterizedTest
    @MethodSource("dropShapes")
    void theTilesCoverTheWholeSurface(DripCuboid.Extent cuboid) {
        double width = 2.0 * cuboid.halfWidth();
        double surface = 2.0 * width * width + 4.0 * width * cuboid.height();
        double tiled = DripCuboid.tiles(cuboid).stream()
                .mapToDouble(tile -> 4.0 * tile.halfSize() * tile.halfSize()).sum();

        assertEquals(surface, tiled, TOLERANCE);
    }

    @Test
    void theHangShapeTilesEachSideWithTwoSquaresAndTheCubeWithOne() {
        assertEquals(6, DripCuboid.tiles(new DripCuboid.Extent(BOTTOM_Y, HALF, 2f * HALF)).size());
        assertEquals(16, DripCuboid.tiles(new DripCuboid.Extent(BOTTOM_Y, HALF, HALF)).size());
    }

    @Test
    void aCuboidWithNoVolumeDrawsNoTile() {
        assertEquals(List.of(), DripCuboid.tiles(new DripCuboid.Extent(BOTTOM_Y, 0f, 0f)));
    }
}
