package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.program.ScanVisualStep;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Bulb's dart of light leaves the cast origin it was handed, the lifted
 * palm blob, and reaches the place the crystal forms
 * (decision spontaneous-abilities-cast-at-the-target-with-no-thrown-blob).
 */
class CastDartTest {

    private static final Vec3 CAST_ORIGIN = new Vec3(1.25, 64.6, -3.5);
    private static final Vec3 PLACE = new Vec3(9.5, 63.0, 4.5);
    private static final ScanVisualStep LIGHT_DART = new ScanVisualStep("light_dart", Optional.empty());

    @Test
    void theDartStartsAtTheCastOrigin() {
        assertEquals(CAST_ORIGIN, CastDart.wave(CAST_ORIGIN, PLACE, LIGHT_DART).from());
    }

    @Test
    void theDartReachesThePlace() {
        assertEquals(PLACE, CastDart.wave(CAST_ORIGIN, PLACE, LIGHT_DART).to());
    }
}
