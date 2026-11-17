package com.mercuriusxeno.goo.client.particle;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.util.List;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Where a splat's quad lies: in the plane of the face its drip struck, on a
 * top, a side or an underside, a clearance out from that face along its
 * normal, each splat in turn at its own clearance so overlapping splats
 * share no plane (decision diagnose-and-correct-the-spray-splat-lying-flat-and-z-fighting).
 */
class SplatPlacementTest {

    private static final Vec3 STRUCK = new Vec3(3.25, 64.0, -7.5);
    private static final double HALF_SIZE = 0.1;
    private static final double SAME = 1e-6;

    @ParameterizedTest
    @EnumSource(value = Direction.class, names = {"UP", "EAST", "NORTH", "DOWN"})
    void theQuadLiesInTheStruckFacesPlaneAClearanceOut(Direction face) {
        double clearance = SplatPlacement.clearance(0);
        List<Vec3> corners = SplatPlacement.corners(STRUCK, face, clearance, HALF_SIZE);

        double plane = face.getAxis().choose(STRUCK.x, STRUCK.y, STRUCK.z)
                + face.getAxisDirection().getStep() * clearance;
        for (Vec3 corner : corners) {
            assertEquals(plane, face.getAxis().choose(corner.x, corner.y, corner.z), SAME,
                    face + " corner " + corner + " should lie in the plane " + plane);
        }
        assertEquals(4, corners.size());
    }

    @ParameterizedTest
    @EnumSource(value = Direction.class, names = {"UP", "EAST", "NORTH", "DOWN"})
    void theQuadSpreadsAcrossTheFace(Direction face) {
        List<Vec3> corners = SplatPlacement.corners(STRUCK, face, SplatPlacement.clearance(0), HALF_SIZE);

        double spread = corners.getFirst().distanceTo(corners.get(2));
        assertEquals(2 * Math.sqrt(2) * HALF_SIZE, spread, SAME, face + " quad should span its whole size");
    }

    @Test
    void everySplatStandsClearOfItsFace() {
        IntStream.range(0, 64).forEach(splat -> assertTrue(SplatPlacement.clearance(splat) > 0,
                "splat " + splat + " should stand clear of its face"));
    }

    @Test
    void consecutiveSplatsLieInDistinctPlanes() {
        for (int splat = 0; splat < 64; splat++) {
            assertNotEquals(SplatPlacement.clearance(splat), SplatPlacement.clearance(splat + 1),
                    "splats " + splat + " and " + (splat + 1) + " should not share a plane");
        }
    }

    @Test
    void noFaceLiesFlatOnATop() {
        assertEquals(Direction.UP, SplatPlacement.faceOf(0, 0, 0));
        assertEquals(Direction.WEST, SplatPlacement.faceOf(-1, 0, 0));
    }
}
