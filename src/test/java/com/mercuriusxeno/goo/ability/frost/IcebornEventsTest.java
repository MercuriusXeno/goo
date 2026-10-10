package com.mercuriusxeno.goo.ability.frost;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * An Iceborn player's snowball strikes with ice, aggravated, doubled,
 * against a fire-immune mob.
 */
class IcebornEventsTest {

    @Test
    void aSnowballStrikesWithIce() {
        assertEquals(IcebornEvents.SNOWBALL_ICE_DAMAGE, IcebornEvents.snowballDamage(false));
    }

    @Test
    void aSnowballStrikesAFireImmuneMobDoubly() {
        assertEquals(IcebornEvents.SNOWBALL_ICE_DAMAGE * 2, IcebornEvents.snowballDamage(true));
    }
}
