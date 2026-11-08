package com.mercuriusxeno.goo.ability;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * nether_decay.json, read from the classpath as the loader reads it: Decay's
 * bite seeks the nearest mob within 45 degrees either side of its aim and 8
 * blocks out, past the 6 it paints blocks within (decisions
 * decay-gnats-degrade-each-block-once, decay-paints-the-one-block-under-the-crosshair).
 */
class NetherDecayJsonTest {

    private static final double SEEK_CONE_DEGREES = 90;
    private static final double SEEK_RANGE = 8;
    private static final double PAINT_RANGE = 6;
    private static final double DELTA = 1e-9;

    private final Delivery decay = AbilityJson.decode("nether_decay").delivery();

    @Test
    void theBiteSeeksWithinFortyFiveDegreesEitherSideOfTheAim() {
        assertEquals(SEEK_CONE_DEGREES, decay.coneDegrees(), DELTA);
    }

    @Test
    void theBiteSeeksTwoBlocksPastWhereTheSwarmPaints() {
        assertEquals(SEEK_RANGE, decay.livingReach(), DELTA);
        assertEquals(PAINT_RANGE, decay.range(), DELTA);
    }
}
