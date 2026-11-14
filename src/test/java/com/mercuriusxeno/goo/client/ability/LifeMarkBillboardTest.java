package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.zoo.Kin;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Covers which mobs draw a life mark, how strongly, and where its parts
 * stand: a rallied or charmed mob draws it whole always, a mob Kin senses
 * draws it whole with no touch while Kin stands, and a mob only Heal or
 * Drain touched keeps TouchedMobs' fade. TouchedMobs' fade is read through
 * its own method, so no fade constant is pinned here.
 * leader-shows-life-bars-while-it-stands
 * rallied-and-charmed-mobs-always-show-their-health-bar
 * rally-and-charm-icons-sit-left-of-the-heart
 */
class LifeMarkBillboardTest {

    private static final Vec3 CENTER = new Vec3(0, 64, 0);
    private static final int SENSED_ID = 11;
    private static final int TOUCHED_ID = 12;
    private static final int UNMARKED_ID = 13;
    private static final long TICK = 500L;
    private static final float EPSILON = 1e-6f;
    /** A mob neither rallied nor charmed. */
    private static final boolean BARE = false;
    /** A rallied or charmed mob, wearing a head mark. */
    private static final boolean HEAD_MARKED = true;
    /** A mob on its feet. */
    private static final boolean STANDING = false;
    /** A mob lying downed. */
    private static final boolean DOWNED = true;

    private final TouchedMobs touched = new TouchedMobs();
    private final KinSense kin = new KinSense();

    @BeforeEach
    void senseOneMobAndTouchAnother() {
        Mob sensed = mock(Mob.class);
        when(sensed.getId()).thenReturn(SENSED_ID);
        when(sensed.distanceToSqr(CENTER)).thenReturn(4.0);
        kin.sense(new Kin(12, 4), List.of(sensed), CENTER, TICK);
        touched.touch(TOUCHED_ID, TICK);
    }

    @Test
    void sensedMobDrawsItsLifeMarkWholeWithoutATouch() {
        for (float later = 0; later < 200; later += 10) {
            float time = TICK + later;
            assertEquals(1f, LifeMarkBillboard.markStrength(SENSED_ID, time, touched, kin, BARE, STANDING), EPSILON,
                    "at " + time);
        }
    }

    @Nested
    class HeadMarkedMob {

        private final TouchedMobs untouched = new TouchedMobs();
        private final KinSense kinInactive = new KinSense();

        @Test
        void showsItsLifeMarkWholeWithNoTouchAndNoKin() {
            for (long later = 0; later < 2000; later += 37) {
                long tick = TICK + later;
                assertTrue(LifeMarkBillboard.showsLifeMark(UNMARKED_ID, tick, untouched, kinInactive, HEAD_MARKED,
                        STANDING), "at " + tick);
                assertEquals(1f, LifeMarkBillboard.markStrength(UNMARKED_ID, tick + 0.5f, untouched, kinInactive,
                        HEAD_MARKED, STANDING), EPSILON, "at " + tick);
            }
        }
    }

    @Test
    void touchedMobOutsideKinKeepsItsFade() {
        for (float later = 0; later < 60; later += 1.5f) {
            float time = TICK + later;
            assertEquals(touched.opacity(TOUCHED_ID, time),
                    LifeMarkBillboard.markStrength(TOUCHED_ID, time, touched, kin, BARE, STANDING), EPSILON,
                    "at " + time);
        }
    }

    /**
     * A downed mob whose rally ended, untouched and unsensed, draws its mark whole.
     * downed-mob-shows-a-skull-and-its-negative-health
     */
    @Test
    void downedMobDrawsItsLifeMarkWholeWithNoRally() {
        TouchedMobs untouched = new TouchedMobs();
        KinSense kinInactive = new KinSense();
        assertTrue(LifeMarkBillboard.showsLifeMark(UNMARKED_ID, TICK, untouched, kinInactive, BARE, DOWNED));
        assertEquals(1f, LifeMarkBillboard.markStrength(UNMARKED_ID, TICK, untouched, kinInactive, BARE, DOWNED),
                EPSILON);
    }

    @Nested
    class MarkLayout {

        private static final float NARROW_COUNT = 5f;
        private static final float WIDE_COUNT = 31f;

        @Test
        void heartDrawsTwoThirdsOfItsOldSize() {
            assertEquals(0.15f, LifeMarkBillboard.Layout.of(NARROW_COUNT, BARE).heartWidth(), EPSILON);
        }

        @Test
        void countDrawsAtHalfTheOldFont() {
            assertEquals(0.0125f, LifeMarkBillboard.Layout.of(NARROW_COUNT, BARE).countScale(), EPSILON);
        }

        @Test
        void markStandsCenteredOverTheHead() {
            for (float countWidth : new float[] {NARROW_COUNT, WIDE_COUNT}) {
                LifeMarkBillboard.Layout layout = LifeMarkBillboard.Layout.of(countWidth, BARE);
                assertEquals(-layout.left(), layout.right(), EPSILON, "count width " + countWidth);
            }
        }

        @Test
        void countLineCentersOnTheHeartsMiddle() {
            for (float countWidth : new float[] {NARROW_COUNT, WIDE_COUNT}) {
                LifeMarkBillboard.Layout layout = LifeMarkBillboard.Layout.of(countWidth, BARE);
                assertEquals(layout.heartMiddle(), layout.countMiddle(), EPSILON, "count width " + countWidth);
            }
        }

        @Test
        void countStandsRightOfTheHeart() {
            LifeMarkBillboard.Layout layout = LifeMarkBillboard.Layout.of(NARROW_COUNT, BARE);
            assertTrue(layout.countLeft() > layout.heartLeft() + layout.heartWidth());
            assertEquals(layout.right(), layout.countLeft() + NARROW_COUNT * layout.countScale(), EPSILON);
        }

        @Test
        void bareMobDrawsTheHeartAtTheRowsLeftEdge() {
            for (float countWidth : new float[] {NARROW_COUNT, WIDE_COUNT}) {
                LifeMarkBillboard.Layout layout = LifeMarkBillboard.Layout.of(countWidth, BARE);
                assertEquals(layout.left(), layout.heartLeft(), EPSILON, "count width " + countWidth);
                assertEquals(-layout.left(), layout.right(), EPSILON, "count width " + countWidth);
            }
        }

        @Test
        void iconDrawsTheHeartsSize() {
            LifeMarkBillboard.Layout layout = LifeMarkBillboard.Layout.of(NARROW_COUNT, HEAD_MARKED);
            assertEquals(layout.heartWidth(), layout.iconWidth(), EPSILON);
            assertEquals(0.15f, layout.iconWidth(), EPSILON);
        }

        @Test
        void iconHeartAndCountStandLeftToRightInOneRow() {
            for (float countWidth : new float[] {NARROW_COUNT, WIDE_COUNT}) {
                LifeMarkBillboard.Layout layout = LifeMarkBillboard.Layout.of(countWidth, HEAD_MARKED);
                String at = "count width " + countWidth;
                assertTrue(layout.iconLeft() + layout.iconWidth() < layout.heartLeft(), at);
                assertTrue(layout.heartLeft() + layout.heartWidth() < layout.countLeft(), at);
                assertEquals(layout.heartTop(), layout.iconTop(), EPSILON, at);
                assertEquals(layout.heartBottom(), layout.iconBottom(), EPSILON, at);
                assertEquals(layout.left(), layout.iconLeft(), EPSILON, at);
                assertEquals(-layout.left(), layout.right(), EPSILON, at);
            }
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
        assertTrue(LifeMarkBillboard.showsLifeMark(SENSED_ID, TICK, touched, kin, BARE, STANDING));
        assertTrue(LifeMarkBillboard.showsLifeMark(TOUCHED_ID, TICK, touched, kin, BARE, STANDING));
        assertFalse(LifeMarkBillboard.showsLifeMark(UNMARKED_ID, TICK, touched, kin, BARE, STANDING));
    }
}
