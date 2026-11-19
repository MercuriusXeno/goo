package com.mercuriusxeno.goo.ability.root;

import com.mercuriusxeno.goo.ability.AbilityBadge;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.ability.DeliveryKind;
import com.mercuriusxeno.goo.ability.program.DripLanding;
import com.mercuriusxeno.goo.ability.program.VinesStep;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * Leaf's world goo ships as leaf_vines.json, a residual stream spray whose
 * drips land through the vines step.
 * vines-is-sprayed-slime-that-entangles-what-tries-to-cross
 * residual-badge-marks-the-four-goo-sprays
 */
class VinesAbilityTest {

    private static final String VINES = "leaf_vines";

    @Test
    void vinesWearsTheResidualBadgeAndSpraysAsAStream() {
        AbilityDefinition vines = AbilityJson.decode(VINES);

        assertEquals(List.of(AbilityBadge.RESIDUAL, DeliveryKind.STREAM),
                List.of(vines.badge(), vines.delivery().kind()));
    }

    @Test
    void vinesDripsLandThroughTheVinesStep() {
        assertInstanceOf(VinesStep.class, DripLanding.in(AbilityJson.decode(VINES).behaviors()).orElseThrow());
    }
}
