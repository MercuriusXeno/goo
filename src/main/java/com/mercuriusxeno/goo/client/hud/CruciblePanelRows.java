package com.mercuriusxeno.goo.client.hud;

import com.mercuriusxeno.goo.block.crucible.CrucibleBlockEntity;
import com.mercuriusxeno.goo.block.crucible.CrucibleMeltQueue;
import com.mercuriusxeno.goo.item.GooContents;
import com.mercuriusxeno.goo.item.GooFormat;
import com.mercuriusxeno.goo.item.PartiallyMeltedItem;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Supplies the crucible HUD panel's rows: the melting item's row led by its model, one grid
 * of a "reservoir / total" cell per goo type, then a heat row per fuel holding heat or stock
 * (decisions one-panel-painter-takes-rows, heat-row-reads-seconds, pool-keeps-stacks-in-order).
 */
final class CruciblePanelRows {

    /** Separator color (dim gray). */
    private static final int SEPARATOR_COLOR = 0xFF888888;
    /** Separator between reservoir and total volumes. */
    private static final String VOLUME_SEPARATOR = " / ";
    /**
     * The widest type row text under 10K, which every type row measures at
     * least (decision crucible-panel-floors-width-under-ten-thousand).
     */
    static final String SUB_TEN_THOUSAND_FLOOR_TEXT = "9.99K" + VOLUME_SEPARATOR + "9.99K";
    /** Percent in a whole fraction. */
    private static final int PERCENT = 100;
    /** Suffix after the dissolved percent. */
    private static final String PERCENT_SIGN = "%";
    /** Prefix before the count of stacks waiting. */
    private static final String WAITING_PREFIX = " +";

    private CruciblePanelRows() {
    }

    /**
     * Returns the rows for a crucible, empty when it holds neither goo nor heat.
     *
     * @param be the crucible block entity
     * @return the rows top to bottom
     */
    static List<PanelRow> rows(CrucibleBlockEntity be) {
        List<PanelRow> rows = new ArrayList<>();
        PanelRow melting = meltRow(be.meltHead(), be.meltWaiting().size());
        if (melting != null) {
            rows.add(melting);
        }
        rows.addAll(rows(be.getReservoir(), poolContents(be), CrucibleFuelDisplay.heatRows(be.burnForecast())));
        return rows;
    }

    /**
     * Builds the melting item's row: its model, how far it has dissolved in percent,
     * then a dim count of the stacks waiting behind it.
     *
     * @param head    the stack dissolving, or null when nothing melts
     * @param waiting the number of stacks waiting behind the head
     * @return the row, or null when nothing melts
     */
    static @Nullable PanelRow meltRow(CrucibleMeltQueue.@Nullable Entry head, int waiting) {
        if (head == null) {
            return null;
        }
        List<PanelRow.TextSegment> segments = new ArrayList<>();
        segments.add(new PanelRow.TextSegment((int) (head.dissolveFraction() * PERCENT) + PERCENT_SIGN,
                PanelPainter.TEXT_COLOR));
        if (waiting > 0) {
            segments.add(new PanelRow.TextSegment(WAITING_PREFIX + waiting, SEPARATOR_COLOR));
        }
        return PanelRow.itemRow(head.item(), segments);
    }

    /**
     * Returns one grid of the types in the reservoir or the pool, then the heat rows.
     * decision one-class-lays-every-huds-goo-values
     *
     * @param reservoir the reservoir goo contents
     * @param pool      the melt pool goo contents
     * @param heatRows  the heat rows, empty when the crucible holds neither heat nor fuel goo
     * @return the rows top to bottom
     */
    static List<PanelRow> rows(GooContents reservoir, GooContents pool, List<PanelRow> heatRows) {
        List<PanelRow> rows = new ArrayList<>();
        List<PanelAmountGrid.Cell> cells = new ArrayList<>();
        for (ResourceKey<GooTypeDefinition> type : allTypes(reservoir, pool)) {
            cells.add(typeCell(type, volumeOf(reservoir, type), totalOf(reservoir, pool, type)));
        }
        if (!cells.isEmpty()) {
            rows.add(PanelRow.amounts(new PanelAmountGrid(cells)));
        }
        rows.addAll(heatRows);
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
     * Builds one type's cell: icon, reservoir volume, a dim separator, total volume,
     * floored at the widest text under 10K so the panel holds still while draining.
     *
     * @param type         the goo type
     * @param reservoirVol the reservoir volume in mB
     * @param totalVol     the total volume in mB
     * @return the cell
     */
    private static PanelAmountGrid.Cell typeCell(ResourceKey<GooTypeDefinition> type, int reservoirVol,
                                                 long totalVol) {
        return new PanelAmountGrid.Cell(PanelPainter.gooIcon(type), List.of(
                new PanelRow.TextSegment(GooFormat.formatAmount(reservoirVol),
                        PanelPainter.TEXT_COLOR),
                new PanelRow.TextSegment(VOLUME_SEPARATOR, SEPARATOR_COLOR),
                new PanelRow.TextSegment(GooFormat.formatAmount(totalVol),
                        PanelPainter.TEXT_COLOR)), SUB_TEN_THOUSAND_FLOOR_TEXT);
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
