package com.mercuriusxeno.goo.client.throwing;

import com.mercuriusxeno.goo.client.hud.MixedNumberLabel;
import com.mercuriusxeno.goo.data.GooFraction;
import com.mercuriusxeno.goo.item.GooFormat;
import org.junit.jupiter.api.Test;
import java.util.function.ToIntFunction;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A goo slot's amount label reads the stack's exact value as a mixed number,
 * laid by the spacing rule as every mixed number drawn in play is.
 * decision goo-item-carries-its-exact-value-as-a-mixed-remainder
 * decision every-mixed-number-takes-the-spacing-rule
 */
class GooVolumeDecoratorTest {

    /** A font giving each character one pixel. */
    private static final ToIntFunction<String> ONE_PIXEL_EACH = String::length;

    @Test
    void labelReadsTheWholeAndThePartAsOneMixedNumber() {
        assertEquals(GooFormat.formatMixed(GooFraction.of(64, 3)),
                GooVolumeDecorator.volumeLabel(21, GooFraction.of(1, 3), ONE_PIXEL_EACH).text());
    }

    @Test
    void labelOfAPartAloneReadsThatPart() {
        assertEquals(GooFormat.formatMixed(GooFraction.of(1, 3)),
                GooVolumeDecorator.volumeLabel(0, GooFraction.of(1, 3), ONE_PIXEL_EACH).text());
    }

    @Test
    void labelLaysItsMixedNumberByTheSpacingRule() {
        MixedNumberLabel label = GooVolumeDecorator.volumeLabel(21, GooFraction.of(1, 3), ONE_PIXEL_EACH);
        assertEquals(MixedNumberLabel.lay(label.text(), ONE_PIXEL_EACH), label);
        assertTrue(label.holdsMixedNumber());
    }
}
