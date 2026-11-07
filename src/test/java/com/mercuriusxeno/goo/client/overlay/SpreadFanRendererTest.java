package com.mercuriusxeno.goo.client.overlay;

import com.mercuriusxeno.goo.ability.AbilityJson;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Knives shows its spread fan only while right click holds an armed press,
 * and a blob arc ability shows none (decision knives-aims-a-spread-fan-not-a-blob-line).
 */
class SpreadFanRendererTest {

    private static boolean shows(String name, boolean armed) {
        return SpreadFanRenderer.showsFan(AbilityJson.decode(name).delivery(), armed);
    }

    @Test
    void knivesWithAnArmedPressShowsTheFan() {
        assertTrue(shows("crystal_knives", true));
    }

    @Test
    void knivesSelectedAndIdleShowsNoFan() {
        assertFalse(shows("crystal_knives", false));
    }

    @Test
    void aBlobArcShowsNoFan() {
        assertFalse(shows("frost_orb", true));
    }
}
