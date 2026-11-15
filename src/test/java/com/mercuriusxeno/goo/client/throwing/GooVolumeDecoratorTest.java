package com.mercuriusxeno.goo.client.throwing;

import com.mercuriusxeno.goo.data.GooFraction;
import com.mercuriusxeno.goo.item.GooFormat;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A goo slot's amount label reads the stack's exact value as a mixed number.
 * decision goo-item-carries-its-exact-value-as-a-mixed-remainder
 */
class GooVolumeDecoratorTest {

    @Test
    void labelReadsTheWholeAndThePartAsOneMixedNumber() {
        assertEquals(GooFormat.formatMixed(GooFraction.of(64, 3)),
                GooVolumeDecorator.volumeLabel(21, GooFraction.of(1, 3)));
    }

    @Test
    void labelOfAPartAloneReadsThatPart() {
        assertEquals(GooFormat.formatMixed(GooFraction.of(1, 3)),
                GooVolumeDecorator.volumeLabel(0, GooFraction.of(1, 3)));
    }
}
