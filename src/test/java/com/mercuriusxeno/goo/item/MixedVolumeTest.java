package com.mercuriusxeno.goo.item;

import com.mercuriusxeno.goo.data.GooFraction;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The combine the cursor combine, the slot absorb and the quick-craft merge share adds two
 * goo values exactly, a whole the parts make carrying into the whole mB.
 * decision goo-item-carries-its-exact-value-as-a-mixed-remainder
 */
class MixedVolumeTest {

    @Test
    void partsSumAsFractionsAndCarryTheirWhole() {
        MixedVolume joined = new MixedVolume(2, GooFraction.of(1, 2)).plus(new MixedVolume(3, GooFraction.of(2, 3)));

        assertEquals(new MixedVolume(6, GooFraction.of(1, 6)), joined);
    }

    @Test
    void twoHalvesMakeOneWholeMb() {
        MixedVolume joined = new MixedVolume(0, GooFraction.of(1, 2)).plus(new MixedVolume(0, GooFraction.of(1, 2)));

        assertEquals(new MixedVolume(1, GooFraction.ZERO), joined);
    }
}
