package com.mercuriusxeno.goo.client.hud;

import com.mercuriusxeno.goo.block.crystallizer.CrystallizerPhases;
import com.mercuriusxeno.goo.item.ChrysmTier;
import com.mercuriusxeno.goo.item.GooFormat;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import net.minecraft.resources.ResourceKey;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * The crystal panel's rows over plain values: the tier reached so far as a header,
 * then the forming goo's icon beside the crystallized volume over the dial tier's
 * volume, with no time left (decision crystal-panel-reads-goo-and-progress).
 */
final class CrystallizerPanelRows {

    private static final int TIER_COLOR = 0xFFE0C8FF;
    private static final String OVER = " / ";

    private CrystallizerPanelRows() {
    }

    /**
     * @param formingType  the goo the crystal forms from
     * @param crystallized the crystallized volume, in mB
     * @param knobTier     the tier the dial stops at
     * @param tierName     the name a tier reads under
     * @return the tier header where a tier is reached, then the goo row
     */
    static List<PanelRow> rows(ResourceKey<GooTypeDefinition> formingType, long crystallized, ChrysmTier knobTier,
                               Function<ChrysmTier, String> tierName) {
        List<PanelRow> rows = new ArrayList<>();
        ChrysmTier reached = CrystallizerPhases.reachedTier(crystallized);
        if (reached != null) {
            rows.add(PanelRow.header(tierName.apply(reached), TIER_COLOR));
        }
        rows.add(PanelPainter.gooRow(formingType, GooFormat.formatAmount(crystallized) + OVER
                + GooFormat.formatAmount(knobTier.volume())));
        return rows;
    }
}
