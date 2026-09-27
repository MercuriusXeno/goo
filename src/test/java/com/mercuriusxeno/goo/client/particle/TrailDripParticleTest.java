package com.mercuriusxeno.goo.client.particle;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** The trail-drip spawns straight into its fall, with no hang (decision tap-drop-swells-then-falls). */
class TrailDripParticleTest {

    @Test
    void trailDripSpawnsWithZeroHangTicks() {
        assertEquals(0, new TrailDripParticle.Provider(null).hangTicks());
    }

    @Test
    void trailDripDrawsItsCameraFacingSprite() {
        assertFalse(new TrailDripParticle.Provider(null).drawsCuboid());
    }
}
