package com.mercuriusxeno.goo.ability;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Knives' shipped charge ramps its knife count and cone linearly from its
 * no-charge floors to its full charge (decisions shards-sling-then-morph-to-flechettes,
 * knives-cone-five-to-forty-five-and-three-to-twenty-seven-knives).
 */
class ChargeTest {

    private final Charge knives = AbilityJson.decode("crystal_knives").delivery().charge();

    @Test
    void onlyAChargeNamingFlecksSlings() {
        assertTrue(knives.slings());
        assertFalse(Charge.of(30).slings());
        assertEquals(Charge.NONE, Charge.of(0));
    }
}
