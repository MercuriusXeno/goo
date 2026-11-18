package com.mercuriusxeno.goo.ability.root;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * What a hit on a thing does to the vines holding it: any damage ends every
 * vine, the cell that last gave vines keeps one layer fewer, and fire to a
 * held thing that burns lands at the fire factor.
 * vines-deal-no-thorn-damage-and-attacking-the-mob-frees-it
 * entangled-mob-taking-damage-eats-a-layer-of-vines-goo
 */
class RootStrikeTest {

    private static final float FIRE_FACTOR = 1.5f;
    private static final int FULL = 15;
    private static final float EPSILON = 1e-6f;

    @Test
    void aHitOnFiveVinesEndsThemAllAndEatsALayer() {
        assertEquals(new RootStrike(0, 3f, FULL - 1), RootStrike.of(5, 3f, false, FIRE_FACTOR, FULL));
    }

    @Test
    void fireOfFourOnAHeldThingThatBurnsLandsAsSix() {
        assertEquals(6f, RootStrike.of(5, 4f, true, FIRE_FACTOR, FULL).damage(), EPSILON);
    }

    @Test
    void fireOfFourOnAFireImmuneHeldThingLandsAsFour() {
        assertEquals(4f, RootStrike.of(5, 4f, false, FIRE_FACTOR, FULL).damage(), EPSILON);
    }

    @Test
    void aHitWhereTheCellIsGoneEatsNothing() {
        assertEquals(0, RootStrike.of(2, 3f, false, FIRE_FACTOR, 0).layersKept());
    }

    @Test
    void aHitOnAThingNoVinesHoldLeavesItAllAsItWas() {
        assertEquals(new RootStrike(0, 4f, FULL), RootStrike.of(0, 4f, true, FIRE_FACTOR, FULL));
    }
}
