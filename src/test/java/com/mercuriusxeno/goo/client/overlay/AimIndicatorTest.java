package com.mercuriusxeno.goo.client.overlay;

import com.mercuriusxeno.goo.ability.AbilityBadge;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Each badge chooses its own aim mark: mob the entity outline, world the block
 * outline, and every other badge nothing, leaving the vanilla crosshair to aim
 * (decisions target-kind-configured-per-ability,
 * vanilla-crosshair-aims-everything-without-reticule-line-or-touch-ring).
 */
class AimIndicatorTest {

    @ParameterizedTest
    @CsvSource({"MOB, ENTITY_OUTLINE", "WORLD, BLOCK_OUTLINE", "SELF, NONE", "DURATIVE, NONE",
            "FREE, NONE", "CHANNELED, NONE", "PRISM, NONE"})
    void eachBadgeDrawsItsOwnMark(AbilityBadge badge, AimIndicator expected) {
        assertEquals(expected, AimIndicator.of(badge, false));
    }

    @Test
    void anUnsyncedAbilityDrawsNothing() {
        assertEquals(AimIndicator.NONE, AimIndicator.of(null, false));
    }

    /** Grab, a channeled badge, outlines the mob its press holds (decision grab-hides-the-reticle-once-a-mob-is-held). */
    @Test
    void aGrabHoldingAMobDrawsItsOutline() {
        assertEquals(AimIndicator.ENTITY_OUTLINE, AimIndicator.of(AbilityBadge.CHANNELED, true));
    }
}
