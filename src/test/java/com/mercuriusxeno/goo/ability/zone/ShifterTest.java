package com.mercuriusxeno.goo.ability.zone;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Elude cast from the glove stands until its held effect ends
 * (decision self-effects-trickle-until-ended).
 */
class ShifterTest {

    @Test
    void aHeldCastNeverFades() {
        assertEquals(Shifter.NEVER_EXPIRES, Shifter.NONE.hold(8f).expiresAt());
    }
}
