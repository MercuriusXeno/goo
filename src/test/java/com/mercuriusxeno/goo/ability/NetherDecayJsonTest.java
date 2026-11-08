package com.mercuriusxeno.goo.ability;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * nether_decay.json, read from the classpath as the loader reads it: Decay's
 * bite seeks the nearest mob within its delivery's 30-degree cone, which
 * nothing that paints blocks reads (decisions decay-gnats-degrade-each-block-once,
 * decay-paints-the-one-block-under-the-crosshair).
 */
class NetherDecayJsonTest {

    private static final double SEEK_CONE_DEGREES = 30;
    private static final double DELTA = 1e-9;

    @Test
    void theBiteSeeksWithinAThirtyDegreeCone() {
        assertEquals(SEEK_CONE_DEGREES, AbilityJson.decode("nether_decay").delivery().coneDegrees(), DELTA);
    }
}
