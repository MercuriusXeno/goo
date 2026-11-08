package com.mercuriusxeno.goo.ability.world;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that a meteor appears its fall height above its target and falls in a straight line to the target over its
 * fall time, staying there past it; it reads MeteorFall alone, never the
 * entity class, which needs a mod loader (decision meteo-needs-a-clear-sky).
 */
class MeteorTest {

    private static final Vec3 TARGET = new Vec3(4.5, 65.5, -2.5);
    private static final Vec3 START = TARGET.add(0, MeteorFall.FALL_HEIGHT, 0);
    private static final int FALL_TICKS = 60;
    /** How far above its target the meteor must appear: far beyond the 48 blocks it popped in at. */
    private static final int HIGH_HIGH_UP = 200;

    /**
     * It appears high, high above its target, over the world's top where
     * the world is low, so it is seen coming from far off rather than popping
     * in just over the target (operator ruling 2026-11-06).
     */
    @Test
    void appearsHighHighAboveItsTarget() {
        assertEquals(TARGET.add(0, HIGH_HIGH_UP, 0), MeteorFall.startAbove(TARGET));
    }

    /**
     * It falls as a rock falls, from rest and gathering speed: at its start
     * at first, a quarter of the way down at half its fall time, and at its
     * target when the fall time runs out (operator ruling 2026-11-06: it
     * falls at once, speeding up, rather than hanging first).
     */
    @Test
    void fallsFromRestGatheringSpeed() {
        assertEquals(START, MeteorFall.positionAt(START, TARGET, 0, FALL_TICKS));
        assertEquals(TARGET.y + MeteorFall.FALL_HEIGHT * 3 / 4.0,
                MeteorFall.positionAt(START, TARGET, FALL_TICKS / 2, FALL_TICKS).y, 1e-9);
        assertEquals(TARGET, MeteorFall.positionAt(START, TARGET, FALL_TICKS, FALL_TICKS));
    }

    /**
     * It leaves its start on its first tick and falls further every tick
     * after, never hanging still.
     */
    @Test
    void leavesItsStartAtOnceAndFallsFurtherEveryTick() {
        double before = START.y;
        double drop = 0;
        for (int tick = 1; tick <= FALL_TICKS; tick++) {
            double now = MeteorFall.positionAt(START, TARGET, tick, FALL_TICKS).y;
            assertTrue(before - now > drop, "tick " + tick + " falls further than the last");
            drop = before - now;
            before = now;
        }
    }

    /**
     * A frame between two ticks draws the rock between the two ticks' points,
     * so it comes down smoothly rather than stepping a tick's fall at a time
     * (decision meteo-winds-up-then-drops-in-a-second-trailing-fire).
     */
    @Test
    void aFrameBetweenTicksStandsBetweenTheirPoints() {
        double tenth = MeteorFall.positionAt(START, TARGET, 10, FALL_TICKS).y;
        double eleventh = MeteorFall.positionAt(START, TARGET, 11, FALL_TICKS).y;
        double between = MeteorFall.positionAt(START, TARGET, 10.5, FALL_TICKS).y;

        assertTrue(between < tenth && between > eleventh, "between the ticks at " + between);
    }

    /**
     * Past its fall time it stays at the target rather than falling through.
     */
    @Test
    void staysAtTheTargetPastItsFallTime() {
        assertEquals(TARGET, MeteorFall.positionAt(START, TARGET, FALL_TICKS * 2, FALL_TICKS));
    }
}
