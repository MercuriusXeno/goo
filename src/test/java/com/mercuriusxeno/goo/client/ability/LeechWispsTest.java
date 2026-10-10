package com.mercuriusxeno.goo.client.ability;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Covers Lifetap's splash count: one splash per point of health healed, at
 * least one for any heal, and no more than the cap however large the heal.
 */
class LeechWispsTest {

    @Test
    void eachPointHealedSplashesOnce() {
        assertEquals(3, LeechWisps.splashesFor(3.2f));
    }

    @Test
    void aSliverOfHealingStillSplashes() {
        assertEquals(1, LeechWisps.splashesFor(0.2f));
    }

    @Test
    void aHugeHealSplashesUpToTheCap() {
        assertEquals(LeechWisps.MOST_SPLASHES, LeechWisps.splashesFor(40f));
    }
}
