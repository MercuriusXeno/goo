package com.mercuriusxeno.goo.client.overlay;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.util.Arrays;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The free and channeled reticule rings the aimed point in the plane facing
 * the camera and holds its size on screen (decision target-kind-configured-per-ability).
 */
class ReticuleRendererTest {

    private static final double TOLERANCE = 1e-9;
    private static final int TICKS = 4;

    @ParameterizedTest
    @CsvSource({"10, 0, 0", "0, 0, -20", "0, 30, 0", "-3, -8, 5"})
    void theRingCirclesThePointFacingTheCamera(double dx, double dy, double dz) {
        Vec3 camera = new Vec3(1, 70, 1);
        Vec3 point = camera.add(dx, dy, dz);
        Vec3 view = point.subtract(camera).normalize();
        double radius = 0.4;

        Vec3[] ring = ReticuleRenderer.reticuleLines(point, camera, radius).get(0);

        assertEquals(ReticuleRenderer.RING_SEGMENTS + 1, ring.length);
        assertAll(Arrays.stream(ring).map(vertex -> () -> {
            assertEquals(radius, vertex.distanceTo(point), TOLERANCE);
            assertEquals(0, vertex.subtract(point).dot(view), TOLERANCE);
        }));
    }

    @Test
    void fourTicksCrossTheRingFromInsideToOutside() {
        Vec3 point = new Vec3(0, 64, 0);
        double radius = 0.5;

        List<Vec3[]> lines = ReticuleRenderer.reticuleLines(point, new Vec3(0, 64, -10), radius);

        assertEquals(1 + TICKS, lines.size());
        assertAll(lines.subList(1, lines.size()).stream().map(tick -> () -> {
            assertEquals(radius * ReticuleRenderer.TICK_INNER_SHARE, tick[0].distanceTo(point), TOLERANCE);
            assertEquals(radius * ReticuleRenderer.TICK_OUTER_SHARE, tick[1].distanceTo(point), TOLERANCE);
        }));
    }

    @Test
    void theReticuleStandsOffTheFaceTowardTheCamera() {
        Vec3 point = new Vec3(4, 64, 9);
        Vec3 camera = new Vec3(4, 64, -1);
        double radius = 0.25;

        Vec3 drawn = ReticuleRenderer.standOffPoint(point, camera, radius);

        assertEquals(new Vec3(4, 64, 9 - radius * ReticuleRenderer.STAND_OFF_SHARE), drawn);
    }

    @Test
    void aPointBesideTheCameraStandsOffNoFurtherThanHalfway() {
        Vec3 camera = new Vec3(0, 64, 0);

        Vec3 drawn = ReticuleRenderer.standOffPoint(new Vec3(0.4, 64, 0), camera, 1.0);

        assertEquals(0.2, drawn.x, TOLERANCE);
    }

    @Test
    void theRadiusGrowsWithDistanceAboveAFloor() {
        assertEquals(ReticuleRenderer.MIN_RADIUS, ReticuleRenderer.radiusAt(0.5), TOLERANCE);
        assertEquals(40 * ReticuleRenderer.RADIUS_PER_BLOCK, ReticuleRenderer.radiusAt(40), TOLERANCE);
    }
}
