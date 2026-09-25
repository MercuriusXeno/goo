package com.mercuriusxeno.goo.client.hud;

import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.block.crucible.CrucibleBlockEntity;
import com.mercuriusxeno.goo.client.GooTooltipHandler;
import com.mercuriusxeno.goo.item.GooContents;
import com.mercuriusxeno.goo.item.PartiallyMeltedItem;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Supplies the crucible HUD panel's rows: one "reservoir / total" row per goo
 * type, then the heat row while the crucible holds heat or fuel goo
 * (decisions one-panel-painter-takes-rows, fuel-goo-heats-per-mb).
 */
final class CruciblePanelRows {

    /** Separator color (dim gray). */
    private static final int SEPARATOR_COLOR = 0xFF888888;
    /** Separator between reservoir and total volumes. */
    private static final String VOLUME_SEPARATOR = " / ";

    private CruciblePanelRows() {
    }

    /**
     * Returns the rows for a crucible, empty when it holds neither goo nor heat.
     *
     * @param be the crucible block entity
     * @return the rows top to bottom
     */
    static List<PanelRow> rows(CrucibleBlockEntity be) {
        return rows(be.getReservoir(), poolContents(be), CrucibleFuelDisplay.heatRow(be.heatTicks(), be.fuelGooVolume()));
    }

    /**
     * Returns one row per type in the reservoir or the pool, then the fuel row.
     *
     * @param reservoir the reservoir goo contents
     * @param pool      the melt pool goo contents
     * @param fuelRow   the heat row, or null when the crucible holds neither heat nor fuel goo
     * @return the rows top to bottom
     */
    static List<PanelRow> rows(GooContents reservoir, GooContents pool, @Nullable PanelRow fuelRow) {
        List<PanelRow> rows = new ArrayList<>();
        for (ResourceKey<GooTypeDefinition> type : allTypes(reservoir, pool)) {
            rows.add(typeRow(type, volumeOf(reservoir, type), totalOf(reservoir, pool, type)));
        }
        if (fuelRow != null) {
            rows.add(fuelRow);
        }
        return rows;
    }

    /**
     * Returns one type's reservoir and pool volumes together, a long so two
     * full stores never wrap (decision diagnose-then-fix-crucible-overflow).
     *
     * @param reservoir the reservoir goo contents
     * @param pool      the melt pool goo contents
     * @param type      the goo type
     * @return the type's total volume in mB
     */
    static long totalOf(GooContents reservoir, GooContents pool, ResourceKey<GooTypeDefinition> type) {
        return (long) volumeOf(reservoir, type) + volumeOf(pool, type);
    }

    /**
     * Builds one type row: icon, reservoir volume, a dim separator, total volume.
     *
     * @param type         the goo type
     * @param reservoirVol the reservoir volume in mB
     * @param totalVol     the total volume in mB
     * @return the row
     */
    private static PanelRow typeRow(ResourceKey<GooTypeDefinition> type, int reservoirVol, long totalVol) {
        return new PanelRow(PanelPainter.gooIcon(type), List.of(
                new PanelRow.TextSegment(GooTooltipHandler.formatFluidDisplayCompact(reservoirVol),
                        PanelPainter.TEXT_COLOR),
                new PanelRow.TextSegment(VOLUME_SEPARATOR, SEPARATOR_COLOR),
                new PanelRow.TextSegment(GooTooltipHandler.formatFluidDisplayCompact(totalVol),
                        PanelPainter.TEXT_COLOR)), false);
    }

    /**
     * Extracts the partially melted item's pool contents from the crucible.
     *
     * @param be the crucible block entity
     * @return the pool contents, or EMPTY when nothing is melting
     */
    private static GooContents poolContents(CrucibleBlockEntity be) {
        if (be.getMeltingItem().isEmpty()) {
            return GooContents.EMPTY;
        }
        return PartiallyMeltedItem.getContents(be.getMeltingItem());
    }

    /**
     * Returns every goo type in the reservoir or the pool, reservoir order first.
     *
     * @param reservoir the reservoir goo contents
     * @param pool      the pool goo contents
     * @return the types in display order
     */
    private static Set<ResourceKey<GooTypeDefinition>> allTypes(GooContents reservoir, GooContents pool) {
        Set<ResourceKey<GooTypeDefinition>> types = new LinkedHashSet<>();
        types.addAll(reservoir.getAll().keySet());
        types.addAll(pool.getAll().keySet());
        return types;
    }

    /**
     * Returns the volume of a type in a GooContents, or 0 if absent.
     *
     * @param contents the goo contents
     * @param type     the goo type
     * @return the volume in mB
     */
    private static int volumeOf(GooContents contents, ResourceKey<GooTypeDefinition> type) {
        return contents.getAll().getOrDefault(type, 0);
    }
}
