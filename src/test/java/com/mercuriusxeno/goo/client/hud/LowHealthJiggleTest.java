package com.mercuriusxeno.goo.client.hud;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mercuriusxeno.goo.ability.hearts.HeartKind;
import com.mercuriusxeno.goo.ability.hearts.HeartOverlay;
import com.mercuriusxeno.goo.ability.reserve.Reserve;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The health bar shakes for low health only when no other ability's filled hearts back it (decision health-bar-stays-steady-while-filled-hearts-stand). */
class LowHealthJiggleTest {

    private static final int ONE_HEART = 2;

    @Test
    void oneHeartWithNothingFilledJiggles() {
        assertTrue(LowHealthJiggle.jiggles(ONE_HEART, 0, 0));
    }

    @Test
    void oneHeartBackedByTenReserveHalvesStaysSteady() {
        Reserve banked = new Reserve(10, 0f, 0, 0f, 0f, 0);
        assertFalse(jiggles(ONE_HEART, HeartOverlay.NONE, banked));
    }

    @Test
    void oneHeartWithStoneOverEveryMissingHeartStaysSteady() {
        HeartOverlay stoned = new HeartOverlay(HeartKind.STONESKIN, List.of(0, 2, 2, 2, 2, 2, 2, 2, 2, 2),
                HeartOverlay.NEVER_EXPIRES, 0L, 0L);
        assertFalse(jiggles(ONE_HEART, stoned, Reserve.NONE));
    }

    @Test
    void oneHeartUnderBarkWithAReserveHalfStaysSteady() {
        HeartOverlay barked = new HeartOverlay(HeartKind.BARKSKIN, List.of(2), HeartOverlay.NEVER_EXPIRES, 0L, 0L);
        Reserve banked = new Reserve(1, 0f, 0, 0f, 0f, 0);
        assertFalse(jiggles(ONE_HEART, barked, banked));
    }

    @Test
    void oneFilledHalfStillJigglesWhileTheSumStaysAtTheThreshold() {
        assertTrue(LowHealthJiggle.jiggles(ONE_HEART, 0, 1));
        assertTrue(LowHealthJiggle.jiggles(ONE_HEART, 0, 2));
        assertFalse(LowHealthJiggle.jiggles(ONE_HEART, 0, 3));
    }

    @Test
    void anEndedOverlaysShieldsFillNothing() {
        HeartOverlay ended = new HeartOverlay(HeartKind.STONESKIN, List.of(2, 2), 0L, 0L, 0L);
        assertTrue(jiggles(ONE_HEART, ended, Reserve.NONE));
    }

    private static boolean jiggles(int health, HeartOverlay overlay, Reserve reserve) {
        return LowHealthJiggle.jiggles(health, 0, LowHealthJiggle.filledHalves(overlay, reserve));
    }
}
