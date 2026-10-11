package com.mercuriusxeno.goo.client.hud;

import com.mercuriusxeno.goo.item.CanisterFluidContent;
import com.mercuriusxeno.goo.item.ContainerCapacity;
import com.mercuriusxeno.goo.item.GooContents;
import com.mercuriusxeno.goo.item.GooFormat;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Supplies the canister HUD panel's rows: label, upgrade level, then a row per
 * goo type, dominant first, or the vanilla fluid row, then the total against
 * capacity (decisions one-panel-painter-takes-rows and canisters-hold-more-than-one-goo-type).
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
        CanisterFluidContent content = data.content();
        List<PanelRow> rows = rows(data.label(), data.compression(), GooContents.EMPTY);
        Map<ResourceKey<GooTypeDefinition>, Integer> goo = content.gooVolumesDominantFirst();
        goo.forEach((type, volume) -> rows.add(PanelPainter.gooRow(type, GooFormat.formatAmount(volume))));
        if (goo.isEmpty() && !content.isEmpty()) {
            rows.add(PanelPainter.fluidRow(content.dominantFluid(), content.totalVolume()));
        }
        if (!content.isEmpty()) {
            rows.add(PanelPainter.fillRow(content.totalVolume(),
                    ContainerCapacity.canisterCapacity(data.compression())));
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
}
