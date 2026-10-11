package com.mercuriusxeno.goo.entity;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests that a meteor falls in a straight line from where it appears to its
 * target over its fall time, and stays at the target past it
 * (decision meteo-needs-a-clear-sky).
 */
class MeteorTest {

    private static final Vec3 TARGET = new Vec3(4.5, 65.5, -2.5);
    private static final Vec3 START = TARGET.add(0, Meteor.FALL_HEIGHT, 0);
    private static final int FALL_TICKS = 60;

    /**
     * It stands at its start, halfway down at half its fall time, and at its
     * target when the fall time runs out.
     */
    @Test
    void fallsStraightDownOverItsFallTime() {
        assertEquals(START, Meteor.fallPosition(START, TARGET, 0, FALL_TICKS));
        assertEquals(new Vec3(TARGET.x, TARGET.y + Meteor.FALL_HEIGHT / 2.0, TARGET.z),
                Meteor.fallPosition(START, TARGET, FALL_TICKS / 2, FALL_TICKS));
        assertEquals(TARGET, Meteor.fallPosition(START, TARGET, FALL_TICKS, FALL_TICKS));
    }

    /**
     * Past its fall time it stays at the target rather than falling through.
     */
    @Test
    void staysAtTheTargetPastItsFallTime() {
        assertEquals(TARGET, Meteor.fallPosition(START, TARGET, FALL_TICKS * 2, FALL_TICKS));
    }
}
