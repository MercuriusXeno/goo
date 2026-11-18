package com.mercuriusxeno.goo.ability.root;

import com.mercuriusxeno.goo.ability.hearts.HeartKind;
import com.mercuriusxeno.goo.ability.hearts.HeartOverlay;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Who Vines goo spares: a thing under Barkskin, read off the heart effect
 * standing on it, and no one else.
 * barkskin-makes-you-immune-to-vines
 */
class VineImmunityTest {

    private static HeartOverlay standing(HeartKind kind) {
        return new HeartOverlay(kind, List.of(), HeartOverlay.NEVER_EXPIRES, 0L, 0L);
    }

    @Test
    void barkskinStandingSparesTheThing() {
        assertTrue(VineImmunity.spares(standing(HeartKind.BARKSKIN)));
    }

    @Test
    void theSameThingWithoutBarkskinIsHeld() {
        assertFalse(VineImmunity.spares(HeartOverlay.NONE));
    }

    @Test
    void anotherHeartEffectStandingSparesNothing() {
        assertFalse(VineImmunity.spares(standing(HeartKind.KINDLE)));
    }
}
