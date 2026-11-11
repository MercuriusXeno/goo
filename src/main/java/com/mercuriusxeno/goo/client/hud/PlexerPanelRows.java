package com.mercuriusxeno.goo.client.hud;

import com.mercuriusxeno.goo.data.GooAmountOrder;
import com.mercuriusxeno.goo.data.GooFraction;
import com.mercuriusxeno.goo.data.GooValue;
import com.mercuriusxeno.goo.item.GooFormat;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.Nullable;
import java.util.List;
import java.util.Map;

/**
 * The plexer panel's rows over plain values: what one operation on the target costs, alone, since
 * the cutaway already shows the item (decisions plexer-target-shows-in-a-hud-element and
 * plexer-hud-shows-the-cost-alone).
 */
final class PlexerPanelRows {

    private PlexerPanelRows() {
    }

    /**
     * @param value the target's goo value, or null when the client holds none
     * @return one grid of the costs, or no row when the target costs nothing the client knows
     */
    static List<PanelRow> rows(@Nullable GooValue value) {
        List<PanelAmountGrid.Cell> cells = value == null ? List.of() : costCells(value);
        return cells.isEmpty() ? List.of() : List.of(PanelRow.amounts(new PanelAmountGrid(cells)));
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
