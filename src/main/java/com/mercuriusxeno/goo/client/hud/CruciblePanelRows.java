package com.mercuriusxeno.goo.client.hud;

import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.block.crucible.CrucibleBlockEntity;
import com.mercuriusxeno.goo.block.crucible.CrucibleMeltQueue;
import com.mercuriusxeno.goo.client.GooRenderUtil;
import com.mercuriusxeno.goo.client.GooTooltipHandler;
import com.mercuriusxeno.goo.item.GooContents;
import com.mercuriusxeno.goo.item.PartiallyMeltedItem;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * Supplies the crucible HUD panel's rows: the melting item's row, one "reservoir / total"
 * row per goo type, then a heat row per fuel holding heat or stock
 * (decisions one-panel-painter-takes-rows, heat-row-reads-seconds, pool-keeps-stacks-in-order).
 */
final class CruciblePanelRows {

    /** Separator color (dim gray). */
    private static final int SEPARATOR_COLOR = 0xFF888888;
    /** Separator between reservoir and total volumes. */
    private static final String VOLUME_SEPARATOR = " / ";
    /**
     * The widest type row text under 10 blobs, which every type row measures at
     * least (decision crucible-panel-floors-width-under-ten-blobs).
     */
    static final String SUB_TEN_BLOB_FLOOR_TEXT = "9.99" + VOLUME_SEPARATOR + "9.99";
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
        PanelRow melting = meltRow(be.meltHead(), be.meltWaiting().size(), ItemParticleIcons::of);
        if (melting != null) {
            rows.add(melting);
        }
        rows.addAll(rows(be.getReservoir(), poolContents(be), CrucibleFuelDisplay.heatRows(be.burnForecast())));
        return rows;
    }

    /**
     * Builds the melting item's row: its icon, how far it has dissolved in percent,
     * then a dim count of the stacks waiting behind it.
     *
     * @param head    the stack dissolving, or null when nothing melts
     * @param waiting the number of stacks waiting behind the head
     * @param iconOf  resolves an item id to its icon, or null when it has none
     * @return the row, or null when nothing melts
     */
    static @Nullable PanelRow meltRow(CrucibleMeltQueue.@Nullable Entry head, int waiting,
                                      Function<Identifier, @Nullable ItemIcon> iconOf) {
        if (head == null) {
            return null;
        }
        List<PanelRow.TextSegment> segments = new ArrayList<>();
        segments.add(new PanelRow.TextSegment((int) (head.dissolveFraction() * PERCENT) + PERCENT_SIGN,
                PanelPainter.TEXT_COLOR));
        if (waiting > 0) {
            segments.add(new PanelRow.TextSegment(WAITING_PREFIX + waiting, SEPARATOR_COLOR));
        }
        ItemIcon icon = iconOf.apply(head.item());
        if (icon == null) {
            return new PanelRow(null, segments, false);
        }
        return new PanelRow(icon.texture(), segments, false, null, null, icon.uv());
    }

    /**
     * An item's icon: the atlas holding its sprite and the sprite's region of it.
     *
     * @param texture the atlas texture
     * @param uv      the sprite's region of the atlas
     */
    record ItemIcon(Identifier texture, GooRenderUtil.UvRect uv) {
    }

    /**
     * Returns one row per type in the reservoir or the pool, then the heat rows.
     *
     * @param reservoir the reservoir goo contents
     * @param pool      the melt pool goo contents
     * @param heatRows  the heat rows, empty when the crucible holds neither heat nor fuel goo
     * @return the rows top to bottom
     */
    static List<PanelRow> rows(GooContents reservoir, GooContents pool, List<PanelRow> heatRows) {
        List<PanelRow> rows = new ArrayList<>();
        for (ResourceKey<GooTypeDefinition> type : allTypes(reservoir, pool)) {
            rows.add(typeRow(type, volumeOf(reservoir, type), totalOf(reservoir, pool, type)));
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
     * Builds one type row: icon, reservoir volume, a dim separator, total volume,
     * floored at the widest sub-10-blob text so the panel holds still while draining.
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
                        PanelPainter.TEXT_COLOR)), false, SUB_TEN_BLOB_FLOOR_TEXT);
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
