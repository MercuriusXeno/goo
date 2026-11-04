package com.mercuriusxeno.goo.client.hud;

import com.mercuriusxeno.goo.client.GooRenderUtil;
import com.mercuriusxeno.goo.data.GooFraction;
import com.mercuriusxeno.goo.data.GooValue;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.junit.jupiter.api.Test;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static com.mercuriusxeno.goo.data.TestRecipeBuilder.fraction;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Covers the plexer panel's rows: the target item's icon beside its name, then what it
 * costs to make in each type as a mixed number (decision tooltip-and-plexer-hud-show-a-mixed-number).
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
    void targetReadsItsExactCostAsAMixedNumberInEachType() {
        List<PanelRow> rows = PlexerPanelRows.rows("Carpet", null,
                fraction(GooTypes.LEAF, 64, 3, GooTypes.ROCK, 1, 3));

        assertEquals(3, rows.size());
        PanelRow leaf = rows.get(1);
        assertEquals(PanelPainter.gooIcon(GooTypes.LEAF), leaf.icon());
        assertEquals("21¹⁄₃", leaf.segments().getFirst().text());
        PanelRow rock = rows.get(2);
        assertEquals(PanelPainter.gooIcon(GooTypes.ROCK), rock.icon());
        assertEquals("¹⁄₃", rock.segments().getFirst().text());
    }

    /**
     * The cost rows read the highest amount first, ties alphabetical by type key, in place of the type-id order
     * (decision value-lists-order-highest-first-ties-by-type-key).
     */
    @Test
    void costRowsReadHighestFirstTiesByTypeKey() {
        Map<ResourceKey<GooTypeDefinition>, GooFraction> byTypeId = new LinkedHashMap<>();
        byTypeId.put(GooTypes.GLOW, GooFraction.of(2));
        byTypeId.put(GooTypes.LEAF, GooFraction.of(1, 3));
        byTypeId.put(GooTypes.ROCK, GooFraction.of(9, 4));
        byTypeId.put(GooTypes.BLAZE, GooFraction.of(2));
        GooValue value = mock(GooValue.class);
        when(value.exactAll()).thenReturn(byTypeId);

        List<PanelRow> rows = PlexerPanelRows.rows("Carpet", null, value);

        assertEquals(List.of(GooTypes.ROCK, GooTypes.BLAZE, GooTypes.GLOW, GooTypes.LEAF).stream()
                .map(PanelPainter::gooIcon).toList(), rows.subList(1, rows.size()).stream().map(PanelRow::icon)
                .toList());
    }
}
