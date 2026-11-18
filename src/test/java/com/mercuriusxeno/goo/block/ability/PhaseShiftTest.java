package com.mercuriusxeno.goo.block.ability;

import com.mercuriusxeno.goo.ability.quantum.OutOfPhase;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Which way a living thing's phase shifts on its tick: into phase-out where
 * it stands in Phase goo while in phase, back into phase where its laid
 * phase has faded, and neither otherwise, each shift sounding.
 * phase-goo-puts-what-stands-in-it-out-of-phase
 */
class PhaseShiftTest {

    private static final long NOW = 100L;
    private static final OutOfPhase STANDING = new OutOfPhase(NOW + 2);
    private static final OutOfPhase FADED = new OutOfPhase(NOW);

    @Test
    void aThingShiftsOutOfPhaseOnEnteringTheGooAndBackOnceItsPhaseFades() {
        assertEquals(Map.of("enters the goo", PhaseShift.OUT_OF_PHASE, "stays in the goo", PhaseShift.NONE,
                        "off the goo, phase standing", PhaseShift.NONE, "off the goo, phase faded", PhaseShift.INTO_PHASE,
                        "off the goo, never phased", PhaseShift.NONE, "in the goo, phase faded", PhaseShift.OUT_OF_PHASE),
                Map.of("enters the goo", PhaseShift.of(true, OutOfPhase.NONE, NOW),
                        "stays in the goo", PhaseShift.of(true, STANDING, NOW),
                        "off the goo, phase standing", PhaseShift.of(false, STANDING, NOW),
                        "off the goo, phase faded", PhaseShift.of(false, FADED, NOW),
                        "off the goo, never phased", PhaseShift.of(false, OutOfPhase.NONE, NOW),
                        "in the goo, phase faded", PhaseShift.of(true, FADED, NOW)));
    }
}
