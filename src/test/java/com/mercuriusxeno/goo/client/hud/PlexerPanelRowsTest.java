package com.mercuriusxeno.goo.client.hud;

import com.mercuriusxeno.goo.data.GooFraction;
import com.mercuriusxeno.goo.data.GooValue;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.resources.ResourceKey;
import org.junit.jupiter.api.Test;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static com.mercuriusxeno.goo.data.TestRecipeBuilder.fraction;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the plexer panel's rows: the cost of one operation alone, one grid of its positive costs in each type as a
 * mixed number (decisions plexer-hud-shows-the-cost-alone and tooltip-and-plexer-hud-show-a-mixed-number).
 */
class PlexerPanelRowsTest {

    /** The panel reads one grid block of the costs and nothing else: no item model, no item name. */
    @Test
    void targetReadsItsExactCostAsAMixedNumberInEachType() {
        List<PanelRow> rows = PlexerPanelRows.rows(fraction(GooTypes.LEAF, 64, 3, GooTypes.ROCK, 1, 3));

        assertEquals(List.of(PanelRow.amounts(new PanelAmountGrid(List.of(
                PanelAmountGrid.Cell.amount(GooTypes.LEAF, "21¹⁄₃"),
                PanelAmountGrid.Cell.amount(GooTypes.ROCK, "¹⁄₃"))))), rows);
    }

    /**
     * The cost cells read the highest amount first, ties alphabetical by type key, in place of the type-id order
     * (decision value-lists-order-highest-first-ties-by-type-key).
     */
    @Test
    void costCellsReadHighestFirstTiesByTypeKey() {
        Map<ResourceKey<GooTypeDefinition>, GooFraction> byTypeId = new LinkedHashMap<>();
        byTypeId.put(GooTypes.GLOW, GooFraction.of(2));
        byTypeId.put(GooTypes.LEAF, GooFraction.of(1, 3));
        byTypeId.put(GooTypes.ROCK, GooFraction.of(9, 4));
        byTypeId.put(GooTypes.BLAZE, GooFraction.of(2));
        GooValue value = mock(GooValue.class);
        when(value.exactAll()).thenReturn(byTypeId);

        List<PanelRow> rows = PlexerPanelRows.rows(value);

        assertEquals(1, rows.size());
        assertEquals(List.of(GooTypes.ROCK, GooTypes.BLAZE, GooTypes.GLOW, GooTypes.LEAF).stream()
                .map(PanelPainter::gooIcon).toList(), rows.getFirst().grid().cells().stream()
                .map(PanelAmountGrid.Cell::icon).toList());
    }

    /** A target the client holds no value for lays no row, so no panel paints. */
    @Test
    void targetWithNoValueLaysNoRow() {
        assertTrue(PlexerPanelRows.rows(null).isEmpty());
    }
}
