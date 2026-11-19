package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.AbilityJson;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Phase goo holds what stands in it and what hovers in the zone over its top
 * out of phase, as high as the zone quantum_phase.json ships, so a jump over
 * the goo keeps the phase and only stepping off ends it.
 * phase-goo-puts-what-stands-in-it-out-of-phase
 */
class PhaseGooStepTest {

    private static final double SHIPPED_ZONE = 2.0;
    private static final double JUMP_PEAK = 1.25;
    private static final double OVER_THE_ZONE = 2.1;
    private static final double SUNK_IN_THE_GOO = -0.5;

    @Test
    void theGooHoldsWhatStandsInItOrHoversInItsZone() {
        PhaseGooStep shipped = PhaseGooStep.in(AbilityJson.decode("quantum_phase").behaviors()).orElseThrow();

        assertEquals(Map.of("zone height", SHIPPED_ZONE, "in the goo", true, "at a jump's peak", true,
                        "over the zone", false),
                Map.of("zone height", shipped.zoneHeight(), "in the goo", shipped.holds(SUNK_IN_THE_GOO),
                        "at a jump's peak", shipped.holds(JUMP_PEAK), "over the zone", shipped.holds(OVER_THE_ZONE)));
    }
}
