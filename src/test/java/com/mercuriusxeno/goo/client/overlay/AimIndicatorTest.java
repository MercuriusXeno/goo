package com.mercuriusxeno.goo.client.overlay;

import com.mercuriusxeno.goo.ability.AbilityBadge;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Each badge chooses its own aim mark: mob the entity outline, world the block
 * outline, self nothing, free and channeled a reticule at the point (decision
 * target-kind-configured-per-ability).
 */
class AimIndicatorTest {

    @ParameterizedTest
    @CsvSource({"MOB, ENTITY_OUTLINE", "WORLD, BLOCK_OUTLINE", "SELF, NONE", "DURATIVE, NONE",
            "FREE, RETICULE", "CHANNELED, RETICULE", "PRISM, RETICULE"})
    void eachBadgeDrawsItsOwnMark(AbilityBadge badge, AimIndicator expected) {
        assertEquals(expected, AimIndicator.of(badge, false));
    }

    @Test
    void anUnsyncedAbilityDrawsNothing() {
        assertEquals(AimIndicator.NONE, AimIndicator.of(null, false));
    }

    /** Grab, a channeled badge, trades the reticule for the vanilla crosshair once its press holds a mob. */
    @Nested
    class GrabHold {

        @Test
        void aHeldMobDrawsItsOutlineAndNoReticule() {
            assertEquals(AimIndicator.ENTITY_OUTLINE, AimIndicator.of(AbilityBadge.CHANNELED, true));
        }

        @Test
        void aHeldMobLeavesTheVanillaCrosshairToDraw() {
            boolean reticuleDrawn = AimIndicator.of(AbilityBadge.CHANNELED, true) == AimIndicator.RETICULE;
            assertFalse(GooTargetHighlighter.hidesVanillaCrosshair(reticuleDrawn, 0f));
        }

        @ParameterizedTest
        @CsvSource({"CHANNELED", "FREE"})
        void outsideAGrabHoldThePointBadgesKeepTheReticule(AbilityBadge badge) {
            assertEquals(AimIndicator.RETICULE, AimIndicator.of(badge, false));
        }
    }
}
