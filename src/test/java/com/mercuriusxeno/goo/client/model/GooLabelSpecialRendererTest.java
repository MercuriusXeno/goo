package com.mercuriusxeno.goo.client.model;

import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * The goo label draws the symbol of its goo type, from the type icon sheet, over
 * one paper base (decision goo-label-is-paper-plus-one-mb-of-goo).
 */
class GooLabelSpecialRendererTest {

    private static final Identifier BLAZE_ICON = Identifier.fromNamespaceAndPath("goo", "textures/goo/type/blaze.png");

    @Test
    void aLabelDrawsThePaperBaseUnderItsTypesIcon() {
        assertEquals(List.of(GooLabelSpecialRenderer.PAPER_BASE, BLAZE_ICON),
                GooLabelSpecialRenderer.layersOf(GooTypes.BLAZE));
    }

    @Test
    void twoTypesDrawTwoSymbolsOverTheSamePaper() {
        List<Identifier> blaze = GooLabelSpecialRenderer.layersOf(GooTypes.BLAZE);
        List<Identifier> frost = GooLabelSpecialRenderer.layersOf(GooTypes.FROST);
        assertEquals(blaze.getFirst(), frost.getFirst());
        assertNotEquals(blaze.getLast(), frost.getLast());
    }
}
