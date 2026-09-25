package com.mercuriusxeno.goo.client.hud;

import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.item.CanisterFluidContent;
import com.mercuriusxeno.goo.item.GooContents;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Supplies the canister HUD panel's rows: label, upgrade level, then the goo
 * type row or the vanilla fluid row (decision one-panel-painter-takes-rows).
 */
final class CanisterPanelRows {

    private CanisterPanelRows() {
    }

    /**
     * Returns the rows for a targeted canister slot.
     *
     * @param data the slot's content, label and compression
     * @return the rows top to bottom
     */
    static List<PanelRow> rows(CanisterHudRenderer.SlotData data) {
        GooContents goo = toGooContents(data.content());
        List<PanelRow> rows = rows(data.label(), data.compression(), goo);
        if (goo.isEmpty() && !data.content().isEmpty()) {
            rows.add(PanelPainter.fluidRow(data.content().fluid(), data.content().amount()));
        }
        return rows;
    }

    /**
     * Returns the label and upgrade header rows followed by the goo rows.
     *
     * @param label       the slot label, or null
     * @param compression the canister's compression level
     * @param goo         the canister's goo contents
     * @return the rows top to bottom
     */
    static List<PanelRow> rows(@Nullable String label, int compression, GooContents goo) {
        List<PanelRow> headers = new ArrayList<>();
        if (label != null && !label.isEmpty()) {
            headers.add(PanelPainter.labelRow(label));
        }
        if (compression > 0) {
            headers.add(PanelPainter.upgradeRow(compression));
        }
        return PanelPainter.rows(headers, goo);
    }

    /**
     * Converts single-fluid canister content to GooContents.
     *
     * @param content the single-fluid canister content
     * @return GooContents wrapping the content, or EMPTY when it holds no goo
     */
    private static GooContents toGooContents(CanisterFluidContent content) {
        if (content.isEmpty()) {
            return GooContents.EMPTY;
        }
        ResourceKey<GooTypeDefinition> type = content.getGooType();
        if (type == null) {
            return GooContents.EMPTY;
        }
        return new GooContents(Map.of(type, content.amount()));
    }
}
