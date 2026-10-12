package com.mercuriusxeno.goo.ability.unstable;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A block beside one a busting player breaks breaks with it where it is
 * breakable and no harder than the broken block
 * (decision buster-makes-tools-and-attacks-aoe).
 */
class BusterEventsTest {

    private static final float OBSIDIAN = 50f;
    private static final float REINFORCED_DEEPSLATE = 55f;
    private static final float STONE = 1.5f;
    private static final float BEDROCK = -1f;
    private static final float INSTANT = 0f;

    @Test
    void aBlockAsHardAsTheBrokenOneBreaks() {
        assertTrue(BusterEvents.breaksBeside(OBSIDIAN, OBSIDIAN));
    }

    @Test
    void aSofterBlockBreaksAndAnInstantOneToo() {
        assertTrue(BusterEvents.breaksBeside(STONE, OBSIDIAN));
        assertTrue(BusterEvents.breaksBeside(INSTANT, STONE));
    }

    @Test
    void aHarderBlockStands() {
        assertFalse(BusterEvents.breaksBeside(REINFORCED_DEEPSLATE, OBSIDIAN));
    }

    @Test
    void anUnbreakableBlockStandsWhateverWasBroken() {
        assertFalse(BusterEvents.breaksBeside(BEDROCK, OBSIDIAN));
    }
}
