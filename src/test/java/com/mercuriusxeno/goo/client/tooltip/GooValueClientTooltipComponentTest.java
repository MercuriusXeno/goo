package com.mercuriusxeno.goo.client.tooltip;

import com.mercuriusxeno.goo.client.hud.MixedNumberSpacing;
import com.mercuriusxeno.goo.client.hud.StatedGlyphs;
import com.mercuriusxeno.goo.data.GooFraction;
import com.mercuriusxeno.goo.item.MixedNumber;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Covers the goo value tooltip line's layout: its amount's mixed number by the spacing rule at the tooltip's text
 * scale, then the type's name, the line as wide as that respacing makes it.
 * every-mixed-number-takes-the-spacing-rule
 */
class GooValueClientTooltipComponentTest {

    private static final float TEXT_SCALE = 0.8f;
    private static final int NAME_WIDTH = 25;
    private static final String SEPARATOR = " ";

    @Test
    void aMixedNumberValueLaysItsPartsByTheSpacingRuleAtTheTextScaleAndSizesTheLineToIt() {
        MixedNumberSpacing.Laid laid = MixedNumberSpacing.lay(new MixedNumber("21", "¹", "₃"), StatedGlyphs.VANILLA);
        List<MixedNumberSpacing.Piece> expected = new ArrayList<>();
        laid.pieces().forEach(piece -> expected.add(new MixedNumberSpacing.Piece(piece.text(),
                GooValueClientTooltipComponent.TEXT_LEFT + piece.x() * TEXT_SCALE)));
        expected.add(new MixedNumberSpacing.Piece(SEPARATOR,
                GooValueClientTooltipComponent.TEXT_LEFT + laid.width() * TEXT_SCALE));
        float amountWidth = laid.width() + StatedGlyphs.widthOf(SEPARATOR);

        GooValueClientTooltipComponent.Layout layout = GooValueClientTooltipComponent.layout(GooFraction.of(64, 3),
                StatedGlyphs::widthOf, NAME_WIDTH);

        assertEquals(expected, layout.pieces());
        assertEquals(GooValueClientTooltipComponent.TEXT_LEFT + amountWidth * TEXT_SCALE, layout.nameX());
        assertEquals(GooValueClientTooltipComponent.TEXT_LEFT + Math.round((amountWidth + NAME_WIDTH) * TEXT_SCALE),
                layout.width());
    }

    @Test
    void aWholeValueLaysAsOneRunAsTheFontMeasuresIt() {
        String amount = "22" + SEPARATOR;

        GooValueClientTooltipComponent.Layout layout = GooValueClientTooltipComponent.layout(GooFraction.of(22),
                StatedGlyphs::widthOf, NAME_WIDTH);

        assertEquals(List.of(new MixedNumberSpacing.Piece(amount, GooValueClientTooltipComponent.TEXT_LEFT)),
                layout.pieces());
        assertEquals(GooValueClientTooltipComponent.TEXT_LEFT
                + Math.round((StatedGlyphs.widthOf(amount) + NAME_WIDTH) * TEXT_SCALE), layout.width());
    }
}
