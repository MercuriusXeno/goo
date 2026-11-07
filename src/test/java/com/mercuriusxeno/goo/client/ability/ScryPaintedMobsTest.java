package com.mercuriusxeno.goo.client.ability;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A mob a Scry front crosses is painted: outlined through walls in the
 * sweep's color for the linger the walls glow, then let go; a mob the front
 * has not reached, or has already passed, is not (operator ruling
 * 2026-11-06: Scry paints and highlights mobs, a glowing outline through walls).
 * decision scry-sphere-reveals-faces-and-glistens-mobs
 * decision held-sweeps-share-one-fast-shell-visual
 */
class ScryPaintedMobsTest {

    private static final Vec3 CENTER = new Vec3(0.5, 0.9, 0.5);
    private static final int TINT = 0x8866FF;
    private static final int MOB = 42;
    private static final double HIT_AT = 100;

    @Test
    void aFrontCrossesWhatLiesPastItsLastRadiusAndWithinItsNew() {
        Vec3 sixOut = CENTER.add(6, 0, 0);

        assertTrue(ScryPaintedMobs.crossedBy(CENTER, 4.8, 7.2, sixOut), "between the two fronts");
        assertFalse(ScryPaintedMobs.crossedBy(CENTER, 7.2, 9.6, sixOut), "already passed");
        assertFalse(ScryPaintedMobs.crossedBy(CENTER, 2.4, 4.8, sixOut), "not yet reached");
    }

    @Test
    void aCrossedMobGlowsInTheSweepsColorForTheLingerThenLetsGo() {
        ScryPaintedMobs painted = new ScryPaintedMobs();

        painted.paint(MOB, HIT_AT, TINT);

        assertTrue(painted.isPainted(MOB, HIT_AT), "outlined as the front crosses it");
        assertTrue(painted.isPainted(MOB, HIT_AT + 39), "still outlined just inside the linger");
        assertEquals(TINT, painted.outlineAt(MOB, HIT_AT) & 0xFFFFFF, "in the sweep's color");
        assertFalse(painted.isPainted(MOB, HIT_AT + 40), "let go once the linger runs out");
        assertFalse(painted.isPainted(MOB + 1, HIT_AT), "a mob no front crossed stays plain");
    }

    @Test
    void aMobAFrontCrossesAgainGlowsAfresh() {
        ScryPaintedMobs painted = new ScryPaintedMobs();
        painted.paint(MOB, HIT_AT, TINT);

        painted.paint(MOB, HIT_AT + 30, TINT);

        assertTrue(painted.isPainted(MOB, HIT_AT + 60), "the second front's linger");
    }
}
