package com.mercuriusxeno.goo.client.ability;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A carried signal shows brighter than an idle link; the laser joins relays
 * through RelayNetwork.inLinkReach, the server's own test
 * (decision relay-prism-carries-the-signal-through-air).
 */
class RelayLasersTest {

    @Test
    void aCarriedSignalShowsBrighterThanAnIdleLink() {
        assertTrue(RelayLasers.CARRYING_ALPHA > RelayLasers.IDLE_ALPHA);
    }
}
