package com.mercuriusxeno.goo.ability;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Kinetic Slick's stream reaches twelve blocks, so a runner at full speed
 * lays slick ahead without a perfect angle.
 * slick-sprays-twelve-blocks-ahead
 */
class SlickRangeTest {

    private static final double TWELVE_BLOCKS = 12.0;

    @Test
    void slickSpraysTwelveBlocksAhead() {
        assertEquals(TWELVE_BLOCKS, AbilityJson.decode("kinetic_slick").delivery().range());
    }
}
