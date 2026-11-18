package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.ability.Delivery;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * PalmJavelin, the share of a travel form the lifted palm blob shows over
 * the press, against shipped deliveries: Dart's blob stays the cube its
 * javelins hover over, whatever the lift (decision dart-javelins-hover-over-the-shrinking-blob-cube).
 */
class PalmJavelinTest {

    private static final float NONE = 0f;
    private static final float WHOLE = 1f;
    private static final float HALF = 0.5f;

    private final Delivery dart = AbilityJson.decode("metal_dart").delivery();

    @Test
    void dartsPalmBlobStaysTheCubeItsJavelinsHoverOverWhateverTheLift() {
        assertEquals(NONE, PalmJavelin.morphAt(dart, NONE));
        assertEquals(NONE, PalmJavelin.morphAt(dart, HALF));
        assertEquals(NONE, PalmJavelin.morphAt(dart, WHOLE));
    }

    @Test
    void anotherAbilitysPalmBlobStaysACuboidWhateverTheCharge() {
        assertEquals(NONE, PalmJavelin.morphAt(AbilityJson.decode("blaze_molten").delivery(), WHOLE));
        assertEquals(NONE, PalmJavelin.morphAt(null, WHOLE));
    }
}
