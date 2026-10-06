package com.mercuriusxeno.goo.client.overlay;

import com.mercuriusxeno.goo.ability.AbilityBadge;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Each badge chooses its own aim mark: mob the entity outline, world the block
 * outline, self nothing, free and channeled a reticule at the point (decision
 * target-kind-configured-per-ability).
 */
class AimIndicatorTest {

    @ParameterizedTest
    @CsvSource({"MOB, ENTITY_OUTLINE", "WORLD, BLOCK_OUTLINE", "SELF, NONE", "BREW, NONE",
            "FREE, RETICULE", "CHANNELED, RETICULE", "PRISM, RETICULE", "TAP, RETICULE"})
    void eachBadgeDrawsItsOwnMark(AbilityBadge badge, AimIndicator expected) {
        assertEquals(expected, AimIndicator.of(badge));
    }

    @Test
    void anUnsyncedAbilityDrawsNothing() {
        assertEquals(AimIndicator.NONE, AimIndicator.of(null));
    }
}
