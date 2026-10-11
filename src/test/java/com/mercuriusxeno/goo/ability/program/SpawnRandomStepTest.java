package com.mercuriusxeno.goo.ability.program;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers Spawn's conjure roll: a chance of zero never lands, a whole
 * chance always does, and a part chance lands below its share; and the
 * morph reaches the watchers before the conjured mob does.
 */
class SpawnRandomStepTest {

    private static final float NEVER = 0f;
    private static final float ALWAYS = 100f;
    private static final float TAP_CHANCE = 5f;
    private static final float LOWEST_ROLL = 0f;
    private static final float HIGHEST_ROLL = 0.9999f;

    @Test
    void aChanceOfZeroNeverLands() {
        assertFalse(SpawnRandomStep.rolls(NEVER, LOWEST_ROLL));
        assertFalse(SpawnRandomStep.rolls(NEVER, HIGHEST_ROLL));
    }

    @Test
    void aWholeChanceAlwaysLands() {
        assertTrue(SpawnRandomStep.rolls(ALWAYS, HIGHEST_ROLL));
    }

    @Test
    void aPartChanceLandsBelowItsShare() {
        assertTrue(SpawnRandomStep.rolls(TAP_CHANCE, 0.04f));
        assertFalse(SpawnRandomStep.rolls(TAP_CHANCE, 0.05f));
    }

    @Test
    void theTransformationGoesOutBeforeTheMobIsAdded() {
        List<String> sent = new ArrayList<>();

        SpawnRandomStep.spawnAnnounced("mob", () -> sent.add("transformation"), mob -> sent.add(mob + " spawn"));

        assertEquals(List.of("transformation", "mob spawn"), sent);
    }
}
