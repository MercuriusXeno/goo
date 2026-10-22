package com.mercuriusxeno.goo.client.hud;

import com.mercuriusxeno.goo.client.GooRenderUtil;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import java.util.List;
import static com.mercuriusxeno.goo.data.TestRecipeBuilder.fraction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Covers the plexer panel's rows: the target item's icon beside its name, then what it
 * costs to make in each type (decision values-are-fractions-melt-floors-fabricate-ceils).
 */
class PlexerPanelRowsTest {

    private static final Identifier ATLAS = Identifier.fromNamespaceAndPath("minecraft", "textures/atlas/items.png");
    private static final GooRenderUtil.UvRect DIAMOND_UV = new GooRenderUtil.UvRect(0.25f, 0.5f, 0.375f, 0.625f);

    @Test
    void targetReadsAsOneRowOfItsIconBesideItsName() {
        List<PanelRow> rows = PlexerPanelRows.rows("Diamond", new CruciblePanelRows.ItemIcon(ATLAS, DIAMOND_UV), null);

        assertEquals(1, rows.size());
        PanelRow row = rows.getFirst();
        assertEquals(ATLAS, row.icon());
        assertEquals(DIAMOND_UV, row.iconUv());
        assertEquals(List.of(new PanelRow.TextSegment("Diamond", PanelPainter.TEXT_COLOR)), row.segments());
    }

    @Test
    void targetWithoutAnIconReadsItsNameAlone() {
        List<PanelRow> rows = PlexerPanelRows.rows("Diamond", null, null);

        assertEquals(1, rows.size());
        assertNull(rows.getFirst().icon());
        assertEquals("Diamond", rows.getFirst().segments().getFirst().text());
    }

    @Test
    void targetReadsWhatItCostsToMakeAtTheCeilingOfEachType() {
        List<PanelRow> rows = PlexerPanelRows.rows("Carpet", null, fraction(GooTypes.LEAF, 64, 3));

        assertEquals(2, rows.size());
        PanelRow leaf = rows.get(1);
        assertEquals(PanelPainter.gooIcon(GooTypes.LEAF), leaf.icon());
        assertEquals("22", leaf.segments().getFirst().text());
    }
}
