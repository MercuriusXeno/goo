package com.mercuriusxeno.goo.client.hud;

import com.mercuriusxeno.goo.data.GooAmountOrder;
import com.mercuriusxeno.goo.data.GooFraction;
import com.mercuriusxeno.goo.data.GooValue;
import com.mercuriusxeno.goo.item.GooFormat;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The plexer panel's rows over plain values: the target item's model beside its name
 * (decision plexer-target-shows-in-a-hud-element), then what the target costs to make.
 */
final class PlexerPanelRows {

    private PlexerPanelRows() {
    }

    /**
     * @param itemName the target item's name
     * @param item     the target item's registry id, whose model the name row draws as its icon
     * @param value    the target's goo value, or null when the client holds none
     * @return the name row, the item beside its name, then one grid of the costs
     */
    static List<PanelRow> rows(String itemName, Identifier item, @Nullable GooValue value) {
        List<PanelRow> rows = new ArrayList<>();
        rows.add(PanelRow.itemRow(item, List.of(new PanelRow.TextSegment(itemName, PanelPainter.TEXT_COLOR))));
        if (value != null) {
            List<PanelAmountGrid.Cell> cells = costCells(value);
            if (!cells.isEmpty()) {
                rows.add(PanelRow.amounts(new PanelAmountGrid(cells)));
            }
        }
        return rows;
    }

    /**
     * The target's exact cost in each type, as a mixed number.
     * decision tooltip-and-plexer-hud-show-a-mixed-number
     * decision value-lists-order-highest-first-ties-by-type-key
     * decision one-class-lays-every-huds-goo-values
     *
     * @param value the target's goo value
     * @return one cell per type, its icon beside its cost, the highest cost first, ties by type id
     */
    private static List<PanelAmountGrid.Cell> costCells(GooValue value) {
        return value.exactAll().entrySet().stream()
                .filter(entry -> entry.getValue().isPositive())
                .sorted(GooAmountOrder.highestFirst())
                .map(PlexerPanelRows::costCell)
                .toList();
    }

    private static PanelAmountGrid.Cell costCell(Map.Entry<ResourceKey<GooTypeDefinition>, GooFraction> cost) {
        return PanelAmountGrid.Cell.amount(cost.getKey(), GooFormat.formatMixed(cost.getValue()));
    }
}
