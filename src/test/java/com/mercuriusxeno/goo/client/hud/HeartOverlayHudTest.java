package com.mercuriusxeno.goo.client.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mercuriusxeno.goo.ability.hearts.HeartKind;
import com.mercuriusxeno.goo.ability.hearts.HeartOverlay;
import java.util.List;
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
        HeartOverlay stoned = HeartOverlay.NONE.apply(HeartKind.STONESKIN, 1_200, 10f, 20f, 0.5f, 0L);
        HeartOverlay barked = HeartOverlay.NONE.apply(HeartKind.BARKSKIN, 1_200, 10f, 0L);
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
    void reserveLaysNothingOverTheBar() {
        assertTrue(sprites(HeartKind.RESERVE, 2, 2).isEmpty());
    }

    @Test
    void reserveDrawsItsOwnVitalHeartBehindByTheHalvesBanked() {
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

    private static List<String> sprites(HeartKind kind, int shieldHalves, int realHalves) {
        return HeartOverlayHud.heartSprites(kind, shieldHalves, realHalves).stream().map(Object::toString).toList();
    }
}
