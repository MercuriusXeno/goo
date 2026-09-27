package com.mercuriusxeno.goo.client.particle;

import com.mercuriusxeno.goo.DripFall;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** The tap-drip spawns hanging at the spigot for the hang (decision tap-drop-swells-then-falls). */
class TapDripParticleTest {

    @Test
    void tapDripSpawnsHangingForTheHangTicks() {
        assertEquals(DripFall.HANG_TICKS, new TapDripParticle.Provider(null).hangTicks());
    }
}
