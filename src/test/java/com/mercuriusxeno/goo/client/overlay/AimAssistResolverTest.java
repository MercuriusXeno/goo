package com.mercuriusxeno.goo.client.overlay;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Covers the aim assist answering entities alone: a standing chain marker
 * is never an aim target, so a throw at one lands beside it (decision
 * splat-runs-the-program-no-fuse).
 */
class AimAssistResolverTest {

    @Test
    void anAimHitIsAnEntityAlone() {
        assertEquals(List.of(AimAssistResolver.AimHit.EntityHit.class),
                List.of(AimAssistResolver.AimHit.class.getPermittedSubclasses()));
    }
}
