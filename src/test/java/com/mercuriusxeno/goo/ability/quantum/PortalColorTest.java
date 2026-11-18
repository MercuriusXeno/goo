package com.mercuriusxeno.goo.ability.quantum;

import com.mercuriusxeno.goo.ability.GloveButton;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * With Portal held, the attack button places the blue portal and the use
 * button the orange one
 * (decision portal-takes-over-left-and-right-click-for-blue-and-orange).
 */
class PortalColorTest {

    @Test
    void theAttackButtonPlacesBlue() {
        assertEquals(PortalColor.BLUE, PortalColor.pressedBy(GloveButton.ATTACK));
    }

    @Test
    void theUseButtonPlacesOrange() {
        assertEquals(PortalColor.ORANGE, PortalColor.pressedBy(GloveButton.USE));
    }
}
