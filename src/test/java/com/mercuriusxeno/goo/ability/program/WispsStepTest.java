package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.ability.DeliveryKind;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * Glow's Wisps as glow_wisps.json ships it: a stream spray whose one step
 * lays the Wisps cell where each drip strikes, in place of the hold that
 * flooded the dark with wisps.
 * glow-world-goo-is-named-wisps-and-takes-the-holds-place
 */
class WispsStepTest {

    @Test
    void wispsLaysItsGooByAStreamSpray() {
        AbilityDefinition wisps = AbilityJson.decode("glow_wisps");
        List<Step> program = wisps.behaviors();

        assertEquals(DeliveryKind.STREAM, wisps.delivery().kind());
        assertEquals(1, program.size(), "Wisps runs its cell laying step alone");
        assertInstanceOf(DripLanding.class, assertInstanceOf(WispsStep.class, program.getFirst()));
    }
}
