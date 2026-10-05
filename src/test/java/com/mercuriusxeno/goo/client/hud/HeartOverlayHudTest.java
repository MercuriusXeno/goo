package com.mercuriusxeno.goo.client.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
    void spriteFollowsEmberAndHalf() {
        assertEquals("goo:hud/heart/ember_full", HeartOverlayHud.heartSprite(true, false).toString());
        assertEquals("goo:hud/heart/ember_half", HeartOverlayHud.heartSprite(true, true).toString());
        assertEquals("goo:hud/heart/ash_full", HeartOverlayHud.heartSprite(false, false).toString());
        assertEquals("goo:hud/heart/ash_half", HeartOverlayHud.heartSprite(false, true).toString());
    }
}
