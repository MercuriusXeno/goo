package com.mercuriusxeno.goo.client.particle;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A void mote streaming into Vacuum's glove homes on where the glove is each
 * tick, so it lands in the hand even while the player moves, not where the
 * hand stood when the mote left
 * (decision vacuum-wind-lines-run-straight-and-far-in-void-colors-with-motes).
 */
class MoteHomingTest {

    private static final double CLOSE = 1e-9;
    private static final int FLIGHT_TICKS = 8;

    @Test
    void aMoteLandsOnTheGloveWhereverTheGloveHasMoved() {
        Vec3 at = new Vec3(10, 65, 4);
        Vec3 glove = new Vec3(0, 65, 0);
        Vec3 walking = new Vec3(-0.2, 0, 0.1);
        for (int left = FLIGHT_TICKS; left >= 1; left--) {
            glove = glove.add(walking);
            at = at.add(MoteHoming.stepToward(at, glove, left));
        }

        assertEquals(0, at.distanceTo(glove), CLOSE, "the mote should end in the hand, " + at + " against " + glove);
    }
}
