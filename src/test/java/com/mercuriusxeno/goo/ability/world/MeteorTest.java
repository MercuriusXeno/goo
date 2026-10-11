package com.mercuriusxeno.goo.ability.world;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests that a meteor appears its fall height above its target, no higher
 * than the world's top, and falls in a straight line to the target over its
 * fall time, staying there past it; it reads MeteorFall alone, never the
 * entity class, which needs a mod loader (decision meteo-needs-a-clear-sky).
 */
class MeteorTest {

    private static final Vec3 TARGET = new Vec3(4.5, 65.5, -2.5);
    private static final Vec3 START = TARGET.add(0, MeteorFall.FALL_HEIGHT, 0);
    private static final int WORLD_TOP = 319;
    private static final int LOW_WORLD_TOP = 80;
    private static final int FALL_TICKS = 60;

    /**
     * It appears the fall height above its target, capped at the world's top.
     */
    @Test
    void appearsTheFallHeightAboveItsTargetUnderTheWorldTop() {
        assertEquals(START, MeteorFall.startAbove(TARGET, WORLD_TOP));
        assertEquals(new Vec3(TARGET.x, LOW_WORLD_TOP, TARGET.z), MeteorFall.startAbove(TARGET, LOW_WORLD_TOP));
    }

    /**
     * It stands at its start, halfway down at half its fall time, and at its
     * target when the fall time runs out.
     */
    @Test
    void fallsStraightDownOverItsFallTime() {
        assertEquals(START, MeteorFall.positionAt(START, TARGET, 0, FALL_TICKS));
        assertEquals(new Vec3(TARGET.x, TARGET.y + MeteorFall.FALL_HEIGHT / 2.0, TARGET.z),
                MeteorFall.positionAt(START, TARGET, FALL_TICKS / 2, FALL_TICKS));
        assertEquals(TARGET, MeteorFall.positionAt(START, TARGET, FALL_TICKS, FALL_TICKS));
    }

    /**
     * Past its fall time it stays at the target rather than falling through.
     */
    @Test
    void staysAtTheTargetPastItsFallTime() {
        assertEquals(TARGET, MeteorFall.positionAt(START, TARGET, FALL_TICKS * 2, FALL_TICKS));
    }
}
