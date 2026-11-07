package com.mercuriusxeno.goo.client.ability;

import net.minecraft.world.phys.Vec3;
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
