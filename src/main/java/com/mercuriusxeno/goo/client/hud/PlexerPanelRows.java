package com.mercuriusxeno.goo.client.hud;

import org.jspecify.annotations.Nullable;
import java.util.List;

/**
 * The plexer panel's rows over plain values: the target item's icon beside its name
 * (decision plexer-target-shows-in-a-hud-element).
 */
final class PlexerPanelRows {

    private PlexerPanelRows() {
    }

    /**
     * @param itemName the target item's name
     * @param icon     the target item's particle icon, or null when its model has none
     * @return one row, the icon beside the name, or the name alone without an icon
     */
    static List<PanelRow> rows(String itemName, CruciblePanelRows.@Nullable ItemIcon icon) {
        List<PanelRow.TextSegment> segments = List.of(new PanelRow.TextSegment(itemName, PanelPainter.TEXT_COLOR));
        if (icon == null) {
            return List.of(new PanelRow(null, segments, false));
        }
        return List.of(new PanelRow(icon.texture(), segments, false, null, null, icon.uv()));
    }
}
