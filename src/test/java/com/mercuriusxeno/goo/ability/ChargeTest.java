package com.mercuriusxeno.goo.ability;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Knives' shipped charge slings flecks, and only a charge naming flecks slings
 * (decision knives-sling-then-morph-to-flechettes); Blast's shipped charge
 * opens at 2 blocks and gains a block every 12 ticks of hold up to 9
 * (decisions blast-charges-from-two-to-seven-blocks, blast-eases-between-stages-at-a-second-each).
 */
class ChargeTest {

    private static final double DELTA = 1e-9;

    private final Charge knives = AbilityJson.decode("crystal_knives").delivery().charge();
    private final Charge blast = AbilityJson.decode("unstable_blast").delivery().charge();

    @Test
    void onlyAChargeNamingFlecksSlings() {
        assertTrue(knives.slings());
        assertFalse(Charge.of(30).slings());
        assertEquals(Charge.NONE, Charge.of(0));
    }

    @ParameterizedTest
    @CsvSource({"0, 2", "6, 2", "11, 2", "12, 3", "24, 4", "36, 5", "48, 6", "60, 7", "72, 8", "83, 8", "84, 9",
        "140, 9", "1000, 9"})
    void blastsChargeGainsABlockEveryTwelveTicksFromTwoToNine(int heldTicks, double stage) {
        assertEquals(stage, blast.stageAt(heldTicks), DELTA);
    }
}
