package com.mercuriusxeno.goo.client.ability;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Flames' jet grows out from the glove over a quarter second, burns away from
 * the glove outward over the same quarter second after release
 * (decision flames-pixelated-fire-stream).
 */
class FireStreamTest {

    private static final double RANGE = 6;
    private static final double DELTA = 1e-9;

    /**
     * The jet's wall point as the player sneaks: it resolves along the ray the
     * caller passes, the camera's for the local player, so it eases with the
     * view (decision diagnose-and-correct-eye-aimed-visuals-jumping-on-sneak).
     */
    @Nested
    class OnSneak {

        private static final Vec3 LOOK = new Vec3(0, -0.3, 1).normalize();
        private static final Vec3 HAND = new Vec3(0.3, 65.1, 0.4);

        private static Vec3 wallPointFrom(double eyeHeight) {
            CameraEye.Sight sight = new CameraEye.Sight(new Vec3(0, 64 + eyeHeight, 0), LOOK);
            FireField.Wall wall = FireStream.aimAlong(sight, HAND, 20, FlatWall::clip).wall();
            assertNotNull(wall);
            return wall.at();
        }

        @Test
        void theWallPointMovesWithTheRayOriginsHeight() {
            assertEquals(0.15, wallPointFrom(1.62).y - wallPointFrom(1.47).y, DELTA);
        }

        @ParameterizedTest
        @ValueSource(doubles = {1.62, 1.47, 1.395})
        void anEasingOriginMovesTheWallPointByTheSameEasedAmount(double eyeHeight) {
            Vec3 origin = new Vec3(0, 64 + eyeHeight, 0);

            assertEquals(FlatWall.metAlong(origin, LOOK).y, wallPointFrom(eyeHeight).y, 1e-6);
        }
    }

    @Test
    void frontTravelsTheRangeInAQuarterSecond() {
        assertEquals(0, FireStream.jetSpan(0, Double.NaN, RANGE).front(), DELTA);
        assertEquals(RANGE * 2 / 5, FireStream.jetSpan(2, Double.NaN, RANGE).front(), DELTA);
        assertEquals(RANGE, FireStream.jetSpan(5, Double.NaN, RANGE).front(), DELTA);
        assertEquals(RANGE, FireStream.jetSpan(40, Double.NaN, RANGE).front(), DELTA);
    }

    @Test
    void tailBurnsAwayFromTheGloveAfterRelease() {
        assertEquals(0, FireStream.jetSpan(40, Double.NaN, RANGE).tail(), DELTA);
        assertEquals(RANGE * 3 / 5, FireStream.jetSpan(40, 3, RANGE).tail(), DELTA);
        assertEquals(RANGE, FireStream.jetSpan(40, 5, RANGE).tail(), DELTA);
    }
}
