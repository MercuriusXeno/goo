package com.mercuriusxeno.goo.ability.zone;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Elude cast from the glove stands until its held effect ends
 * (decision self-effects-trickle-until-ended).
 */
class EludeTest {

    @Test
    void aHeldCastNeverFades() {
        assertEquals(Elude.NEVER_EXPIRES, Elude.NONE.hold(8f).expiresAt());
    }
}
