package com.mercuriusxeno.goo.client.ability;

import net.minecraft.util.ARGB;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Under Sculker's vision the clouds go dark with the rest of the far world:
 * they fade out as the vision eases in, draw not at all while it stands
 * full, and come back as it eases out
 * (decision dive-is-a-brew-of-warden-senses-and-free-swimming-through-solid-blocks).
 */
class SculkerVisionTest {

    private static final int WHITE_CLOUD = ARGB.color(204, 0xFFFFFF);

    @Test
    void cloudsDrawAsTheyAreWithNoVision() {
        assertEquals(WHITE_CLOUD, SculkerVision.cloudColor(WHITE_CLOUD, 0f));
        assertFalse(SculkerVision.hidesClouds(0f));
    }

    @Test
    void cloudsFadeAsTheVisionEasesIn() {
        assertEquals(102, ARGB.alpha(SculkerVision.cloudColor(WHITE_CLOUD, 0.5f)));
        assertEquals(0xFFFFFF, ARGB.color(0, SculkerVision.cloudColor(WHITE_CLOUD, 0.5f)) & 0xFFFFFF);
    }

    @Test
    void cloudsDrawNotAtAllUnderTheFullVision() {
        assertTrue(SculkerVision.hidesClouds(1f));
    }
}
