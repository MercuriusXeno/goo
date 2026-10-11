package com.mercuriusxeno.goo.ability;

import com.mercuriusxeno.goo.ability.program.ExplodeStep;
import com.mercuriusxeno.goo.ability.program.ExplosionMarch;
import com.mercuriusxeno.goo.ability.program.HostVariables;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Blast is drag-sized: its JSON wears the drag_sized tag and its explode
 * step's power reads the dragged size as three quarters of it, so the
 * explosion's max reach is the radius dragged and the reference radius 3
 * marches power 2.25, the operator's ruling that the radius is the reach
 * (decision blast-is-drag-sized-like-the-black-hole).
 */
class BlastSizeTest {

    private static final double TOLERANCE = 1e-6;
    private static final double REFERENCE_POWER = 2.25;

    private static ExplodeStep blast() {
        return AbilityJson.decode("unstable_explode").behaviors().stream()
                .filter(ExplodeStep.class::isInstance).map(ExplodeStep.class::cast).findFirst().orElseThrow();
    }

    @Test
    void blastIsDragSizedAndNeverThrownAtAnEntity() {
        AbilityDefinition blast = AbilityJson.decode("unstable_explode");

        assertTrue(blast.hasTag(AbilityTags.DRAG_SIZED));
        assertFalse(blast.hasTag(AbilityTags.ENTITY));
        assertEquals(AbilityArea.NONE, blast.area(), "the drag draws the ghost, not a resting area");
    }

    @ParameterizedTest
    @ValueSource(doubles = {1, 3, 6})
    void theExplosionsMaxReachIsTheRadiusDragged(double size) {
        float power = blast().power().evaluateFloat(HostVariables.sized(size));

        assertEquals(size, ExplosionMarch.maxReach(power), TOLERANCE);
    }

    @Test
    void theReferenceRadiusMarchesPowerTwoAndAQuarter() {
        assertEquals(REFERENCE_POWER, blast().power().evaluateFloat(HostVariables.sized(DragSize.REFERENCE_RADIUS)),
                TOLERANCE);
    }
}
