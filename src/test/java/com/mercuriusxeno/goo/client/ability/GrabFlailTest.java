package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.program.AilmentKind;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A grabbed mob flails while Grab's ailment stands and stops the tick it ends, its overlay fading after (decision grab-holds-and-throws-a-physics-body). */
class GrabFlailTest {

    @Test
    void aStandingGrabFlails() {
        assertTrue(GrabFlail.flails(List.of(new MobAilments.Worn(AilmentKind.GRABBED, 3))));
    }

    @Test
    void aGrabEndedAndFadingFlailsNoMore() {
        assertFalse(GrabFlail.flails(List.of(new MobAilments.Worn(AilmentKind.GRABBED, -3))));
    }

    @Test
    void anotherAilmentFlailsNothing() {
        assertFalse(GrabFlail.flails(List.of(new MobAilments.Worn(AilmentKind.HEX, 3))));
    }
}
