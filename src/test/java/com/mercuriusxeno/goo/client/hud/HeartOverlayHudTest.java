package com.mercuriusxeno.goo.client.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mercuriusxeno.goo.ability.hearts.HeartKind;
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

    private static List<String> sprites(HeartKind kind, int shieldHalves, int realHalves) {
        return HeartOverlayHud.heartSprites(kind, shieldHalves, realHalves).stream().map(Object::toString).toList();
    }
}
