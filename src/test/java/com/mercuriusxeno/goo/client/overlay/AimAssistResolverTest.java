package com.mercuriusxeno.goo.client.overlay;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the aim assist answering entities alone: a standing ability block
 * is never an aim target, so a throw at one lands beside it (decision
 * splat-runs-the-program-no-fuse); and its cone taking no mob beyond the
 * ray's reach (decision grab-reaches-telekinesis-range).
 */
class AimAssistResolverTest {

    @Test
    void anAimHitIsAnEntityAlone() {
        assertEquals(List.of(AimAssistResolver.AimHit.EntityHit.class),
                List.of(AimAssistResolver.AimHit.class.getPermittedSubclasses()));
    }

    @Test
    void theConeTakesAMobWithinTheRaysReachAndNoneBeyondIt() {
        assertTrue(AimAssistResolver.inReach(12, 16));
        assertFalse(AimAssistResolver.inReach(20, 16));
    }
}
