package com.mercuriusxeno.goo.ability.yarn;

import com.mercuriusxeno.goo.ability.AbilityBadge;
import com.mercuriusxeno.goo.ability.AbilityJson;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Yarn's world goo ships as yarn_stitch.json, wearing the residual badge.
 * every-residual-goo-ability-wears-the-residual-badge-and-takes-the-residual-icon
 */
class StitchAbilityTest {

    @Test
    void stitchWearsTheResidualBadge() {
        assertEquals(AbilityBadge.RESIDUAL, AbilityJson.decode("yarn_stitch").badge());
    }
}
