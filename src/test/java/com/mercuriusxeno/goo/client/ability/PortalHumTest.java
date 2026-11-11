package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.registry.GooSoundIds;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A portal's hum loops the shipped hum at its gate's centre, falling off
 * linearly with distance, and stops where it stands in its playing once its
 * gate falls.
 * decision standing-portals-loop-a-soft-ambient-hum
 */
class PortalHumTest {

    private static final Vec3 CENTRE = new Vec3(12.5, 65.0, -3.0);

    @Test
    void loopsTheHumAtItsGatesCentreFallingOffLinearly() {
        PortalHum hum = new PortalHum(CENTRE);
        assertTrue(hum.isLooping(), "the hum plays once rather than looping");
        assertEquals(0, hum.getDelay(), "the loop waits between its plays");
        assertEquals(SoundInstance.Attenuation.LINEAR, hum.getAttenuation());
        assertFalse(hum.isRelative(), "the hum follows the listener rather than standing at its gate");
        assertEquals(CENTRE, new Vec3(hum.getX(), hum.getY(), hum.getZ()));
        assertEquals(GooSoundIds.PORTAL_HUM, hum.getIdentifier());
    }

    @Test
    void stopsTheTickItsGateFalls() {
        PortalHum hum = new PortalHum(CENTRE);
        hum.fall();
        assertTrue(hum.isStopped(), "the hum plays on after its gate fell");
    }
}
