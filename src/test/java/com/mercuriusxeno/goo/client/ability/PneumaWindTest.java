package com.mercuriusxeno.goo.client.ability;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pneuma's inhale: each mote leaves ahead of the eye within the spread and is
 * drawn into the mouth, quickening as it nears, fading in and out (decision
 * pneuma-overfills-the-air-meter-with-a-brief-airless-immunity).
 */
class PneumaWindTest {

    private static final double EPSILON = 1e-9;
    private static final Vec3 LOOK_EAST = new Vec3(1, 0, 0);
    private static final PneumaWind.Mote MOTE = new PneumaWind.Mote(
            PneumaWind.startAhead(LOOK_EAST, 0, 1), PneumaWind.mouth(LOOK_EAST), 0L);

    /**
     * Where the motes ride as the player sneaks: the entity eye has stepped to
     * crouch height while the camera still eases toward it (decision
     * diagnose-and-correct-eye-drawn-visuals-jumping-on-sneak).
     */
    @Nested
    class OnSneak {

        private static final Vec3 LOOK = new Vec3(0, 0, 1);
        private static final CameraEye.Sight EASING_CAMERA = new CameraEye.Sight(new Vec3(0, 65.47, 0), LOOK);
        private static final CameraEye.Sight CROUCHED_EYE = new CameraEye.Sight(new Vec3(0, 65.27, 0), LOOK);

        @Test
        void theLocalPlayersMotesInFirstPersonRideTheCamera() {
            Vec3 origin = CameraEye.chosen(true, EASING_CAMERA, CROUCHED_EYE).eye();

            assertEquals(EASING_CAMERA.eye().add(PneumaWind.motePoint(MOTE, 3)), PneumaWind.moteAt(origin, MOTE, 3));
        }

        @Test
        void motesSeenFromOutsideTheLocalFirstPersonViewRideTheEntityEye() {
            Vec3 origin = CameraEye.chosen(false, EASING_CAMERA, CROUCHED_EYE).eye();

            assertEquals(CROUCHED_EYE.eye().add(PneumaWind.motePoint(MOTE, 3)), PneumaWind.moteAt(origin, MOTE, 3));
        }
    }

    @Test
    void aMoteLeavesAheadOfTheEyeWithinTheSpread() {
        Vec3 start = PneumaWind.motePoint(MOTE, 0);

        assertEquals(PneumaWind.REACH, start.x, EPSILON);
        assertEquals(PneumaWind.SPREAD, Math.hypot(start.y, start.z), EPSILON);
    }

    @Test
    void aMoteEndsAtTheMouth() {
        Vec3 end = PneumaWind.motePoint(MOTE, PneumaWind.LIFE_TICKS);

        assertEquals(PneumaWind.MOUTH_AHEAD, end.x, EPSILON);
        assertEquals(-PneumaWind.MOUTH_DROP, end.y, EPSILON);
    }

    @Test
    void aMoteQuickensAsItNearsTheMouth() {
        double firstHalf = PneumaWind.motePoint(MOTE, 0).distanceTo(PneumaWind.motePoint(MOTE, PneumaWind.LIFE_TICKS / 2.0));
        double secondHalf = PneumaWind.motePoint(MOTE, PneumaWind.LIFE_TICKS / 2.0)
                .distanceTo(PneumaWind.motePoint(MOTE, PneumaWind.LIFE_TICKS));

        assertTrue(secondHalf > firstHalf, firstHalf + " then " + secondHalf);
    }

    @Test
    void aMoteFadesInAsItLeavesAndOutAtTheMouth() {
        assertEquals(0, PneumaWind.opacity(0), EPSILON);
        assertEquals(1, PneumaWind.opacity(0.5), EPSILON);
        assertEquals(0, PneumaWind.opacity(1), EPSILON);
    }
}
