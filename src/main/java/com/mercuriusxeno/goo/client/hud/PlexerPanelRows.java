package com.mercuriusxeno.goo.client.hud;

import com.mercuriusxeno.goo.data.GooValue;
import com.mercuriusxeno.goo.item.GooFormat;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * The plexer panel's rows over plain values: the target item's icon beside its name
 * (decision plexer-target-shows-in-a-hud-element), then what the target costs to make.
 */
final class PlexerPanelRows {

    private PlexerPanelRows() {
    }

    /**
     * @param itemName the target item's name
     * @param icon     the target item's particle icon, or null when its model has none
     * @param value    the target's goo value, or null when the client holds none
     * @return the name row, the icon beside the name or the name alone, then one cost row per type
     */
    static List<PanelRow> rows(String itemName, CruciblePanelRows.@Nullable ItemIcon icon,
                               @Nullable GooValue value) {
        List<PanelRow> rows = new ArrayList<>();
        rows.add(nameRow(itemName, icon));
        if (value != null) {
            rows.addAll(costRows(value));
        }
        return rows;
    }

    private static PanelRow nameRow(String itemName, CruciblePanelRows.@Nullable ItemIcon icon) {
        List<PanelRow.TextSegment> segments = List.of(new PanelRow.TextSegment(itemName, PanelPainter.TEXT_COLOR));
        if (icon == null) {
            return new PanelRow(null, segments, false);
        }
        return new PanelRow(icon.texture(), segments, false, null, null, icon.uv());
    }

    /**
     * Decision values-are-fractions-melt-floors-fabricate-ceils: the plexer charges the
     * ceiling of each type, so the panel reads the ceiling.
     *
     * @param value the target's goo value
     * @return one row per type, its icon beside its cost, ordered by type id
     */
    private static List<PanelRow> costRows(GooValue value) {
        return value.fabricateCeilingAll().entrySet().stream()
                .sorted(Comparator.comparing(entry -> GooTypes.id(entry.getKey())))
                .map(PlexerPanelRows::costRow)
                .toList();
    }

    private static PanelRow costRow(Map.Entry<ResourceKey<GooTypeDefinition>, Integer> cost) {
        return PanelRow.iconText(PanelPainter.gooIcon(cost.getKey()), GooFormat.formatAmount(cost.getValue()),
                PanelPainter.TEXT_COLOR);
    }
}
