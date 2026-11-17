package com.mercuriusxeno.goo.ability;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Knives ships held, its charge forming knives in 5 stages of 12 ticks, and
 * only a charge naming stages forms knives (decisions
 * held-badge-marks-abilities-that-prepare-and-throw, knives-form-in-fibonacci-stages-to-twelve,
 * knives-stage-takes-twelve-ticks-with-no-cooldown); Blast's shipped charge
 * opens at 2 blocks and gains a block every 12 ticks of hold up to 9
 * (decisions blast-charges-from-two-to-seven-blocks, blast-eases-between-stages-at-a-second-each).
 */
class ChargeTest {

    private static final double DELTA = 1e-9;

    private final AbilityDefinition knivesAbility = AbilityJson.decode("crystal_knives");
    private final Charge knives = knivesAbility.delivery().charge();
    private final Charge blast = AbilityJson.decode("unstable_blast").delivery().charge();

    @Test
    void shippedKnivesWearsHeldAndFormsInFiveStagesOfTwelveTicks() {
        assertEquals(AbilityBadge.HELD, knivesAbility.badge());
        assertEquals(5, knives.stages());
        assertEquals(12, knives.stageTicks());
    }

    @Test
    void onlyAChargeNamingStagesFormsKnives() {
        assertTrue(knives.formsInStages());
        assertFalse(Charge.of(30).formsInStages());
        assertEquals(Charge.NONE, Charge.of(0));
    }

    @ParameterizedTest
    @CsvSource({"0, 2", "6, 2", "11, 2", "12, 3", "24, 4", "36, 5", "48, 6", "60, 7", "72, 8", "83, 8", "84, 9",
        "140, 9", "1000, 9"})
    void blastsChargeGainsABlockEveryTwelveTicksFromTwoToNine(int heldTicks, double stage) {
        assertEquals(stage, blast.stageAt(heldTicks), DELTA);
    }
}
