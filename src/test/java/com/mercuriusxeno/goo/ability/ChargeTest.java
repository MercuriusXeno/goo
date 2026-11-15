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
 * opens at 2 blocks and gains a block every 20 ticks of hold up to 7
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
    @CsvSource({"0, 2", "10, 2", "19, 2", "20, 3", "40, 4", "60, 5", "80, 6", "99, 6", "100, 7", "140, 7", "1000, 7"})
    void blastsChargeGainsABlockEveryTwentyTicksFromTwoToSeven(int heldTicks, double stage) {
        assertEquals(stage, blast.stageAt(heldTicks), DELTA);
    }
}
