package com.mercuriusxeno.goo.block.gasket;

import org.junit.jupiter.api.Test;
import java.util.OptionalInt;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A link asked again while it already answers adds nothing, so a loop of links
 * rests (decision relay-adds-dependent-ask-to-own). The relay never reads the
 * fluid it is asked for, so the ask names none.
 */
class DemandRelayTest {

    private static final int RESTING = 64;

    @Test
    void aLoopOfLinksRests() {
        DemandRelay relay = new DemandRelay();
        relay.readDemandFrom(resource -> relay.statedDemand(resource, () -> RESTING));

        assertEquals(OptionalInt.of(RESTING), relay.statedDemand(null, () -> RESTING));
    }
}
