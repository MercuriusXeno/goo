package com.mercuriusxeno.goo.client.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mercuriusxeno.goo.ability.hearts.HeartKind;
import com.mercuriusxeno.goo.ability.hearts.HeartOverlay;
import com.mercuriusxeno.goo.client.fade.Afterglow;
import com.mercuriusxeno.goo.client.fade.Afterglows;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** The overlay's slot layout mirrors vanilla's health bar, and each slot picks its ember or ash sprite (decision overlay-hearts-are-an-elemental-overshield). */
class HeartOverlayHudTest {

    @Test
    void twoRowsKeepTheFullRowHeight() {
        assertEquals(10, HeartOverlayHud.rowHeight(40f, 0));
    }

    @Test
    void eachRowPastTwoSqueezesByAPixel() {
        assertEquals(9, HeartOverlayHud.rowHeight(40f, 4));
    }

    @Test
    void rowHeightNeverDropsUnderThree() {
        assertEquals(3, HeartOverlayHud.rowHeight(400f, 0));
    }

    @Test
    void slotsRunTenToARowStackingUpward() {
        assertEquals(124, HeartOverlayHud.slotX(13, 100));
        assertEquals(191, HeartOverlayHud.slotY(13, 200, 9));
        assertEquals(200, HeartOverlayHud.slotY(9, 200, 9));
    }

    /** Stoneskin's stone shows in a missing heart's empty container (decision stoneskin-stone-hearts-block-regeneration). */
    @Test
    void stoneskinLaysStoneOverAMissingHeart() {
        assertEquals(List.of("goo:hud/heart/stone_full"), sprites(HeartKind.STONESKIN, 2, 0));
        assertEquals(List.of("goo:hud/heart/stone_half"), sprites(HeartKind.STONESKIN, 1, 0));
    }

    /** Vanilla's half heart covers sprite columns 0 to 4, tip included, so the right half starts at column 5. */
    @Test
    void heartHalvesSplitWhereVanillasHalfHeartEnds() {
        assertEquals(0, HeartOverlayHud.halfStart(0));
        assertEquals(5, HeartOverlayHud.halfEnd(0));
        assertEquals(5, HeartOverlayHud.halfStart(1));
        assertEquals(9, HeartOverlayHud.halfEnd(1));
    }

    @Test
    void stoneBesideAHalfHeartDrawsInTheRightHalf() {
        assertTrue(HeartOverlayHud.stoneBesideHalfHeart(HeartKind.STONESKIN, 1, 1));
        assertFalse(HeartOverlayHud.stoneBesideHalfHeart(HeartKind.STONESKIN, 0, 1));
        assertFalse(HeartOverlayHud.stoneBesideHalfHeart(HeartKind.STONESKIN, 2, 0));
        assertFalse(HeartOverlayHud.stoneBesideHalfHeart(HeartKind.BARKSKIN, 1, 1));
    }

    @Test
    void stoneskinPaintsTheMissingSlotsItFillsAndOtherKindsOnlyRealHealth() {
        HeartOverlay stoned = HeartOverlay.NONE.hold(HeartKind.STONESKIN, 10f, 20f, 0.5f, 0L);
        HeartOverlay barked = HeartOverlay.NONE.hold(HeartKind.BARKSKIN, 10f, 10f, HeartOverlay.WHOLE_HIT, 0L);
        assertEquals(10, HeartOverlayHud.paintedSlots(stoned, 10));
        assertEquals(5, HeartOverlayHud.paintedSlots(barked, 10));
        assertEquals(0, HeartOverlayHud.paintedSlots(HeartOverlay.NONE, 10));
    }

    @Test
    void kindleLaysAshUnderAndEmberOverItsHalves() {
        assertEquals(List.of("goo:hud/heart/ash_full", "goo:hud/heart/ember_full"), sprites(HeartKind.KINDLE, 2, 2));
        assertEquals(List.of("goo:hud/heart/ash_full", "goo:hud/heart/ember_half"), sprites(HeartKind.KINDLE, 1, 2));
        assertEquals(List.of("goo:hud/heart/ash_full"), sprites(HeartKind.KINDLE, 0, 2));
    }

    @Test
    void shieldShowsNoMoreThanTheRealHeartUnderIt() {
        assertEquals(List.of("goo:hud/heart/ash_half", "goo:hud/heart/ember_half"), sprites(HeartKind.KINDLE, 2, 1));
        assertEquals(List.of("goo:hud/heart/bark_half"), sprites(HeartKind.BARKSKIN, 2, 1));
    }

    @Test
    void barkskinLaysBarkOverItsHalvesAndLeavesBareHeartsToVanilla() {
        assertEquals(List.of("goo:hud/heart/bark_full"), sprites(HeartKind.BARKSKIN, 2, 2));
        assertEquals(List.of("goo:hud/heart/bark_half"), sprites(HeartKind.BARKSKIN, 1, 2));
        assertTrue(sprites(HeartKind.BARKSKIN, 0, 2).isEmpty());
    }

    @Test
    void reserveDrawsItsOwnHeartBehindByTheHalvesBanked() {
        assertEquals("goo:hud/heart/reserve_full", HeartOverlayHud.reserveSprite(2).orElseThrow().toString());
        assertEquals("goo:hud/heart/reserve_half", HeartOverlayHud.reserveSprite(1).orElseThrow().toString());
        assertTrue(HeartOverlayHud.reserveSprite(0).isEmpty());
    }

    @Test
    void aReserveHeartSitsRaisedAboveTheHeartInFront() {
        assertEquals(198, HeartOverlayHud.reserveY(200));
    }

    // iceborn-frozen-hearts-thaw-on-fire: frozen hearts lie over present hearts only
    @Test
    void icebornLaysFrostOverPresentHalvesOnly() {
        assertEquals(List.of("goo:hud/heart/ice_full"), sprites(HeartKind.ICEBORN, 2, 2));
        assertEquals(List.of("goo:hud/heart/ice_half"), sprites(HeartKind.ICEBORN, 2, 1));
    }

    @Test
    void icebornsCrawlPaintsFrost() {
        assertEquals("goo:hud/heart/ice_full", HeartOverlayHud.crawlSprite(HeartKind.ICEBORN).toString());
        assertEquals("goo:hud/heart/stone_full", HeartOverlayHud.crawlSprite(HeartKind.STONESKIN).toString());
    }

    // scales-crystal-hearts-diamond-blue-overlay: crystal hearts lie over present hearts only
    @Test
    void scalesLaysCrystalOverPresentHalvesAndCrawlsCrystal() {
        assertEquals(List.of("goo:hud/heart/crystal_full"), sprites(HeartKind.SCALES, 2, 2));
        assertEquals(List.of("goo:hud/heart/crystal_half"), sprites(HeartKind.SCALES, 2, 1));
        assertEquals("goo:hud/heart/crystal_full", HeartOverlayHud.crawlSprite(HeartKind.SCALES).toString());
    }

    /** After the heart effect ends, the overlay it last drew lingers and fades over a second (decision brew-effect-visuals-linger-and-fade-over-a-second-after-the-effect-ends). */
    @Nested
    class AfterTheEffectEnds {

        private static final float DELTA = 1e-6f;
        private static final float ENDED_AT = 100f;
        private final HeartOverlay barked = HeartOverlay.NONE.hold(HeartKind.BARKSKIN, 10f, 10f, HeartOverlay.WHOLE_HIT, 0L);
        private final Afterglows<HeartKind, HeartOverlay> glows = new Afterglows<>();

        @BeforeEach
        void endTheBarkAtTheEndTick() {
            HeartOverlayHud.painted(glows, barked, ENDED_AT - 0.05f);
            HeartOverlayHud.painted(glows, HeartOverlay.NONE, ENDED_AT);
        }

        @Test
        void halfASecondAfterTheRememberedOverlayPaintsItsSlotsAtHalfAlpha() {
            HeartOverlayHud.PaintedOverlay painted = HeartOverlayHud.painted(glows, HeartOverlay.NONE,
                    ENDED_AT + Afterglow.FADE_TICKS / 2f).getFirst();
            assertEquals(5, painted.slots(10));
            assertEquals(0.5f, painted.alpha(), DELTA);
        }

        @Test
        void aSecondAfterNoSlotPaints() {
            assertTrue(HeartOverlayHud.painted(glows, HeartOverlay.NONE, ENDED_AT + Afterglow.FADE_TICKS).isEmpty());
        }

        @Test
        void aStandingOverlayPaintsWhole() {
            HeartOverlayHud.PaintedOverlay painted = HeartOverlayHud.painted(glows, barked, ENDED_AT + 1f).getFirst();
            assertEquals(5, painted.slots(10));
            assertEquals(1f, painted.alpha(), DELTA);
        }
    }

    /** A heart effect replaced by another fades out under the new one over a second (decision brew-effect-visuals-linger-and-fade-over-a-second-after-the-effect-ends). */
    @Nested
    class WhenAnotherHeartEffectReplacesIt {

        private static final float DELTA = 1e-6f;
        private static final float REPLACED_AT = 100f;
        private final HeartOverlay kindled = HeartOverlay.NONE.hold(HeartKind.KINDLE, 10f, 10f, HeartOverlay.WHOLE_HIT, 0L);
        private final HeartOverlay barked = HeartOverlay.NONE.hold(HeartKind.BARKSKIN, 10f, 10f, HeartOverlay.WHOLE_HIT, 0L);
        private final Afterglows<HeartKind, HeartOverlay> glows = new Afterglows<>();

        @BeforeEach
        void replaceKindleWithBarkskin() {
            HeartOverlayHud.painted(glows, kindled, REPLACED_AT - 0.05f);
            HeartOverlayHud.painted(glows, barked, REPLACED_AT);
        }

        @Test
        void halfASecondAfterTheReplacedOverlayPaintsAtHalfAlphaUnderTheNewOneWhole() {
            List<HeartOverlayHud.PaintedOverlay> painted = HeartOverlayHud.painted(glows, barked,
                    REPLACED_AT + Afterglow.FADE_TICKS / 2f);
            assertEquals(List.of(HeartKind.KINDLE, HeartKind.BARKSKIN),
                    painted.stream().map(layer -> layer.overlay().kind()).toList());
            assertEquals(0.5f, painted.get(0).alpha(), DELTA);
            assertEquals(1f, painted.get(1).alpha(), DELTA);
        }

        @Test
        void aSecondAfterTheNewOverlayPaintsAlone() {
            List<HeartOverlayHud.PaintedOverlay> painted = HeartOverlayHud.painted(glows, barked,
                    REPLACED_AT + Afterglow.FADE_TICKS);
            assertEquals(List.of(new HeartOverlayHud.PaintedOverlay(barked, 1f)), painted);
        }
    }

    private static List<String> sprites(HeartKind kind, int shieldHalves, int realHalves) {
        return HeartOverlayHud.heartSprites(kind, shieldHalves, realHalves).stream().map(Object::toString).toList();
    }
}
