package com.mercuriusxeno.goo.client.hud;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The reserve shank row lays jelly's shanks by the halves banked on vanilla's hunger slots, counted from the right (decision reserve-channels-on-jelly). */
class ReserveShankHudTest {

    @Test
    void aSlotDrawsJellysShankWholeOrHalfByTheHalvesBanked() {
        assertEquals("goo:hud/food/reserve_shank_full", ReserveShankHud.shankSprite(2).orElseThrow().toString());
        assertEquals("goo:hud/food/reserve_shank_half", ReserveShankHud.shankSprite(1).orElseThrow().toString());
        assertTrue(ReserveShankHud.shankSprite(0).isEmpty());
    }

    @Test
    void slotsCountFromTheBarsRightEndAsVanillaLaysThem() {
        assertEquals(291, ReserveShankHud.slotX(0, 300));
        assertEquals(283, ReserveShankHud.slotX(1, 300));
    }
}
