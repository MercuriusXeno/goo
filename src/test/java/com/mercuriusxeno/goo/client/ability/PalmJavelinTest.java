package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.ability.Delivery;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** PalmJavelin, the share of Dart's javelin the lifted palm blob shows over the press, against shipped deliveries. */
class PalmJavelinTest {

    private static final float NONE = 0f;
    private static final float WHOLE = 1f;
    private static final float HALF = 0.5f;

    private final Delivery dart = AbilityJson.decode("metal_dart").delivery();

    @Test
    void dartsPalmBlobIsTheCuboidAtNoChargeAndTheJavelinAtFullCharge() {
        assertEquals(NONE, PalmJavelin.morphAt(dart, NONE));
        assertEquals(HALF, PalmJavelin.morphAt(dart, HALF));
        assertEquals(WHOLE, PalmJavelin.morphAt(dart, WHOLE));
    }

    @Test
    void anotherAbilitysPalmBlobStaysACuboidWhateverTheCharge() {
        assertEquals(NONE, PalmJavelin.morphAt(AbilityJson.decode("blaze_molten").delivery(), WHOLE));
        assertEquals(NONE, PalmJavelin.morphAt(null, WHOLE));
    }
}
