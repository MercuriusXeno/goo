package com.mercuriusxeno.goo.client.ber.style;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * GlowReflectorStyle: one end of each link draws its beam, and the beam
 * draws brighter the more light the network carries.
 * decision reflector-rails-carry-the-brightest-light
 */
class GlowReflectorStyleTest {

    @Test
    void exactlyOneEndDrawsEachBeam() {
        Vec3[] links = {new Vec3(3, 0, 0), new Vec3(0, -2, 0), new Vec3(0, 0, 7), new Vec3(-5, 4, 5)};
        for (Vec3 link : links) {
            assertNotEquals(GlowReflectorStyle.drawsTheBeam(link), GlowReflectorStyle.drawsTheBeam(link.reverse()),
                    link.toString());
        }
    }

    @Test
    void theBeamBrightensWithTheNetworksLight() {
        assertEquals(1f, GlowReflectorStyle.lightShare(15), 0f);
        assertEquals(1f / 15, GlowReflectorStyle.lightShare(1), 1e-6f);
        assertEquals(0f, GlowReflectorStyle.lightShare(0), 0f);
    }
}
