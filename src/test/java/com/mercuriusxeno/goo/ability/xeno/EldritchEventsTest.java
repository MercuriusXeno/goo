package com.mercuriusxeno.goo.ability.xeno;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Who sees whom across the phase: anything in phase is seen by every player, and the out-of-phase by the
 * eldritch alone.
 */
class EldritchEventsTest {

    // decision eldritch-sight-reveals-the-out-of-phase
    @Test
    void theOutOfPhaseAreSeenByTheEldritchAlone() {
        assertEquals(Map.of("in phase, plain player", true, "in phase, eldritch player", true,
                        "out of phase, plain player", false, "out of phase, eldritch player", true),
                Map.of("in phase, plain player", EldritchEvents.sees(false, false),
                        "in phase, eldritch player", EldritchEvents.sees(false, true),
                        "out of phase, plain player", EldritchEvents.sees(true, false),
                        "out of phase, eldritch player", EldritchEvents.sees(true, true)));
    }
}
