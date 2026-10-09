package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.pulse.RelayNetwork;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The laser joins relays exactly as far apart as the server links them, and
 * a carried signal shows brighter than an idle link
 * (decision relay-prism-carries-the-signal-through-air).
 */
class RelayLasersTest {

    @Test
    void relaysWithinTheRelayRangeLink() {
        assertTrue(RelayLasers.linked(BlockPos.ZERO, new BlockPos(RelayNetwork.RANGE, 0, 0)));
    }

    @Test
    void relaysPastTheRelayRangeDoNotLink() {
        assertFalse(RelayLasers.linked(BlockPos.ZERO, new BlockPos(RelayNetwork.RANGE + 2, 0, 0)));
    }

    @Test
    void aCarriedSignalShowsBrighterThanAnIdleLink() {
        assertTrue(RelayLasers.CARRYING_ALPHA > RelayLasers.IDLE_ALPHA);
    }
}
