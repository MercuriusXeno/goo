package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.zoo.Leader;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Covers which mobs draw a life mark and how strongly: a mob Leader senses
 * draws it whole with no touch while Leader stands, and a mob only Heal or
 * Drain touched keeps TouchedMobs' fade. TouchedMobs' fade is read through
 * its own method, so no fade constant is pinned here.
 * leader-shows-life-bars-while-it-stands
 */
class LifeMarkBillboardTest {

    private static final Vec3 CENTER = new Vec3(0, 64, 0);
    private static final int SENSED_ID = 11;
    private static final int TOUCHED_ID = 12;
    private static final int UNMARKED_ID = 13;
    private static final long TICK = 500L;
    private static final float EPSILON = 1e-6f;

    private final TouchedMobs touched = new TouchedMobs();
    private final LeaderSense leader = new LeaderSense();

    @BeforeEach
    void senseOneMobAndTouchAnother() {
        Mob sensed = mock(Mob.class);
        when(sensed.getId()).thenReturn(SENSED_ID);
        when(sensed.distanceToSqr(CENTER)).thenReturn(4.0);
        leader.sense(new Leader(12, 4), List.of(sensed), CENTER, TICK);
        touched.touch(TOUCHED_ID, TICK);
    }

    @Test
    void sensedMobDrawsItsLifeMarkWholeWithoutATouch() {
        for (float later = 0; later < 200; later += 10) {
            float time = TICK + later;
            assertEquals(1f, LifeMarkBillboard.markStrength(SENSED_ID, time, touched, leader), EPSILON, "at " + time);
        }
    }

    @Test
    void touchedMobOutsideLeaderKeepsItsFade() {
        for (float later = 0; later < 60; later += 1.5f) {
            float time = TICK + later;
            assertEquals(touched.opacity(TOUCHED_ID, time),
                    LifeMarkBillboard.markStrength(TOUCHED_ID, time, touched, leader), EPSILON, "at " + time);
        }
    }

    @Test
    void fullLifeFillsEveryInkedColumn() {
        assertEquals(7, LifeMarkBillboard.filledInkColumns(1f));
    }

    @Test
    void halfLifeFillsHalfTheInkedColumns() {
        assertEquals(4, LifeMarkBillboard.filledInkColumns(0.5f));
    }

    @Test
    void slimLifeFillsOneColumn() {
        assertEquals(1, LifeMarkBillboard.filledInkColumns(0.02f));
    }

    @Test
    void noLifeFillsNoColumn() {
        assertEquals(0, LifeMarkBillboard.filledInkColumns(0f));
    }

    @Test
    void marksDrawForTouchedAndSensedMobsAlike() {
        assertTrue(LifeMarkBillboard.showsLifeMark(SENSED_ID, TICK, touched, leader));
        assertTrue(LifeMarkBillboard.showsLifeMark(TOUCHED_ID, TICK, touched, leader));
        assertFalse(LifeMarkBillboard.showsLifeMark(UNMARKED_ID, TICK, touched, leader));
    }
}
