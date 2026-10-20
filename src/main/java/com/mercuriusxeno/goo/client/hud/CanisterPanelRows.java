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
 * Supplies the canister HUD panel's rows: label, then a row per goo type,
 * dominant first, or the vanilla fluid row, then the total against the
 * capacity the held void grows (decisions one-panel-painter-takes-rows,
 * canisters-hold-more-than-one-goo-type and each-void-mb-adds-n-plus-one-capacity).
 */
final class CanisterPanelRows {

    private CanisterPanelRows() {
    }

    /**
     * Returns the rows for a targeted canister slot.
     *
     * @param data the slot's content and label
     * @return the rows top to bottom
     */
    static List<PanelRow> rows(CanisterHudRenderer.SlotData data) {
        CanisterFluidContent content = data.content();
        List<PanelRow> rows = rows(data.label(), GooContents.EMPTY);
        // decision hud-panel-shows-the-label-type
        if (data.gooLabel() != null) {
            rows.add(PanelPainter.gooLabelRow(data.gooLabel()));
        }
        Map<ResourceKey<GooTypeDefinition>, Integer> goo = content.gooVolumesDominantFirst();
        goo.forEach((type, volume) -> rows.add(PanelPainter.gooRow(type, GooFormat.formatAmount(volume))));
        if (goo.isEmpty() && !content.isEmpty()) {
            rows.add(PanelPainter.fluidRow(content.dominantFluid(), content.totalVolume()));
        }
        if (!content.isEmpty()) {
            rows.add(PanelPainter.fillRow(content.totalVolume(),
                    content.capacityOver(ContainerCapacity.CANISTER_BASE)));
        }
        return rows;
    }

    /**
     * Returns the label header row followed by the goo rows.
     *
     * @param label the slot label, or null
     * @param goo   the canister's goo contents
     * @return the rows top to bottom
     */
    static List<PanelRow> rows(@Nullable String label, GooContents goo) {
        List<PanelRow> headers = new ArrayList<>();
        if (label != null && !label.isEmpty()) {
            headers.add(PanelPainter.labelRow(label));
        }
        return PanelPainter.rows(headers, goo);
    }
}
