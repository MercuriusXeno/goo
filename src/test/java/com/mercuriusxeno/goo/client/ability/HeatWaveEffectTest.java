package com.mercuriusxeno.goo.client.ability;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Flames' heat wave eases toward full heat while Flames runs and away
 * after, and runs only while right click holds Flames
 * (decisions broil-heat-wave-ring-cooks-ahead, flames-absorbs-broil-and-cooks-blocks).
 */
class HeatWaveEffectTest {

    private static final float EPSILON = 1e-6f;

    /**
     * The shimmer's reach point as the player sneaks: it resolves along the
     * ray the caller passes, the camera's for the local player, so it eases
     * with the view (decision diagnose-and-correct-eye-aimed-visuals-jumping-on-sneak).
     */
    @Nested
    class OnSneak {

        private static final Vec3 LOOK = new Vec3(0, -0.3, 1).normalize();

        private static Vec3 reachPointFrom(double eyeHeight) {
            CameraEye.Sight sight = new CameraEye.Sight(new Vec3(0, 64 + eyeHeight, 0), LOOK);
            return HeatWaveEffect.reachAlong(sight, 20, FlatWall::clip, (from, to) -> null);
        }

        @Test
        void theReachPointMovesWithTheRayOriginsHeight() {
            assertEquals(0.15, reachPointFrom(1.62).y - reachPointFrom(1.47).y, 1e-9);
        }

        @ParameterizedTest
        @ValueSource(doubles = {1.62, 1.47, 1.395})
        void anEasingOriginMovesTheReachPointByTheSameEasedAmount(double eyeHeight) {
            Vec3 origin = new Vec3(0, 64 + eyeHeight, 0);

            assertEquals(FlatWall.metAlong(origin, LOOK).y, reachPointFrom(eyeHeight).y, 1e-6);
        }
    }

    @Test
    void aRunningFlameWarmsTheWaveByAShareEachTick() {
        assertEquals(HeatWaveEffect.EASE_PER_TICK, HeatWaveEffect.easedHeat(0f, true, 1f), EPSILON);
    }

    @Test
    void anEndedFlameCoolsTheWave() {
        assertEquals(1f - HeatWaveEffect.EASE_PER_TICK, HeatWaveEffect.easedHeat(1f, false, 1f), EPSILON);
    }

    @Test
    void aLongFrameReachesItsTargetWithoutOvershooting() {
        assertEquals(1f, HeatWaveEffect.easedHeat(0f, true, 100f), EPSILON);
    }

    @Test
    void noSelectedAbilityRunsNoWave() {
        assertFalse(HeatWaveEffect.holdsFlames(null, true));
    }
}
