package com.mercuriusxeno.goo.client.particle;

import com.mercuriusxeno.goo.DripFall;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The tap-drip spawns as a cuboid hanging at the spigot for the hang (decision tap-drop-swells-then-falls). */
class TapDripParticleTest {

    @Test
    void tapDripSpawnsHangingForTheHangTicks() {
        assertEquals(DripFall.HANG_TICKS, new TapDripParticle.Provider(null).hangTicks());
    }

    @Test
    void tapDripDrawsAsACuboid() {
        assertTrue(new TapDripParticle.Provider(null).drawsCuboid());
    }
}
