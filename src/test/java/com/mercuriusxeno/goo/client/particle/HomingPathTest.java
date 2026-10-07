package com.mercuriusxeno.goo.client.particle;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/** A homing mote leaves from its spawn, bows toward its control point and lands on a target read each tick (decision reserve-hearts-sit-behind-the-bar). */
class HomingPathTest {

    private static final double DELTA = 1e-9;
    private final HomingPath path = new HomingPath(new Vec3(0, 0, 0), new Vec3(2, 2, 0));

    @Test
    void aMoteLeavesFromItsSpawn() {
        assertEquals(new Vec3(0, 0, 0), path.at(new Vec3(4, 0, 0), 0));
    }

    @Test
    void aMoteBowsTowardTheControlPointAtMidFlight() {
        Vec3 mid = path.at(new Vec3(4, 0, 0), 0.5);
        assertEquals(2, mid.x, DELTA);
        assertEquals(1, mid.y, DELTA);
    }

    @Test
    void aMoteLandsOnWhereTheTargetStandsNow() {
        assertEquals(new Vec3(7, 1, 3), path.at(new Vec3(7, 1, 3), 1));
    }

    @Test
    void progressPastTheEndStaysOnTheTarget() {
        assertEquals(new Vec3(4, 0, 0), path.at(new Vec3(4, 0, 0), 1.5));
    }
}
