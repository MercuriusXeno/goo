package com.mercuriusxeno.goo.client.hud;

import com.mercuriusxeno.goo.GooConfig;
import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.GooTypes;
import com.mercuriusxeno.goo.block.ValuedStack;
import com.mercuriusxeno.goo.block.crucible.CrucibleHeat;
import com.mercuriusxeno.goo.block.crucible.CrucibleMeltQueue;
import com.mercuriusxeno.goo.block.crucible.FuelGrade;
import com.mercuriusxeno.goo.client.GooRenderUtil;
import com.mercuriusxeno.goo.item.GooContents;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the crucible HUD's rows: the per-type total of reservoir and pool, which reads
 * past the int range rather than wrapping (decision diagnose-then-fix-crucible-overflow),
 * the heat rows reading seconds (decision heat-row-reads-seconds), and the melting item's row
 * (decision pool-keeps-stacks-in-order). Grades are built
 * from GooConfig's defaults, since the unit suite loads no config.
 */
class CruciblePanelRowsTest {

    private static final FuelGrade BLAZE = new FuelGrade(
            GooTypes.BLAZE, GooConfig.DEFAULT_BLAZE_TICKS_PER_MB, GooConfig.DEFAULT_BLAZE_MELT_RATE);
    private static final FuelGrade UNSTABLE = new FuelGrade(
            GooTypes.UNSTABLE, GooConfig.DEFAULT_UNSTABLE_TICKS_PER_MB, GooConfig.DEFAULT_UNSTABLE_MELT_RATE);
    private static final List<FuelGrade> BURN_ORDER = List.of(UNSTABLE, BLAZE);
    private static final int DRAIN = GooConfig.DEFAULT_COMBO_DRAIN_PER_TICK;

    /**
     * Builds the rows the HUD paints for a crucible holding the given heat and reservoir.
     *
     * @param heat      the crucible's heat
     * @param reservoir the reservoir goo contents
     * @return the rows top to bottom
     */
    private static List<PanelRow> rowsFor(CrucibleHeat heat, GooContents reservoir) {
        List<CrucibleHeat.FuelBurn> burns = heat.forecast(BURN_ORDER, DRAIN, type -> volumeOf(reservoir, type));
        return CruciblePanelRows.rows(reservoir, GooContents.EMPTY, CrucibleFuelDisplay.heatRows(burns));
    }

    /**
     * Returns the heat rows alone, the last rows the panel paints.
     *
     * @param heat      the crucible's heat
     * @param reservoir the reservoir goo contents
     * @return the heat rows top to bottom
     */
    private static List<PanelRow> heatRowsFor(CrucibleHeat heat, GooContents reservoir) {
        List<PanelRow> rows = rowsFor(heat, reservoir);
        return rows.subList(reservoir.getAll().size(), rows.size());
    }

    /** A map-backed reservoir the heat burns from. */
    private static final class MapStock implements CrucibleHeat.FuelStock {
        private final Map<ResourceKey<GooTypeDefinition>, Integer> held = new HashMap<>();

        MapStock(Map<ResourceKey<GooTypeDefinition>, Integer> volumes) {
            held.putAll(volumes);
        }

        @Override
        public int volume(ResourceKey<GooTypeDefinition> type) {
            return held.getOrDefault(type, 0);
        }

        @Override
        public int extract(ResourceKey<GooTypeDefinition> type, int amount) {
            int taken = Math.min(amount, volume(type));
            held.put(type, volume(type) - taken);
            return taken;
        }

        GooContents contents() {
            return new GooContents(held);
        }
    }

    private static void assertCombo(PanelRow row, String seconds) {
        assertEquals(PanelPainter.gooIcon(GooTypes.UNSTABLE), row.icon());
        assertEquals(PanelPainter.gooIcon(GooTypes.BLAZE), row.secondIcon());
        assertEquals(seconds, textOf(row));
    }

    private static void assertLone(PanelRow row, ResourceKey<GooTypeDefinition> fuel, String seconds) {
        assertEquals(PanelPainter.gooIcon(fuel), row.icon());
        assertNull(row.secondIcon());
        assertEquals(seconds, textOf(row));
    }

    private static int volumeOf(GooContents contents, ResourceKey<GooTypeDefinition> type) {
        return contents.getAll().getOrDefault(type, 0);
    }

    private static String textOf(PanelRow row) {
        return row.segments().stream().map(PanelRow.TextSegment::text).collect(Collectors.joining());
    }

    /** A diamond a quarter dissolved ahead of a waiting log reads its icon, "25%" and a dim "+1". */
    @Test
    void meltRowShowsHeadIconFractionAndWaitingCount() {
        Identifier diamond = Identifier.fromNamespaceAndPath("minecraft", "diamond");
        Identifier atlas = Identifier.fromNamespaceAndPath("minecraft", "textures/atlas/items.png");
        GooRenderUtil.UvRect diamondUv = new GooRenderUtil.UvRect(0.25f, 0.5f, 0.375f, 0.625f);
        CrucibleMeltQueue queue = new CrucibleMeltQueue();
        queue.appendAll(List.of(new ValuedStack(diamond, 1, 100),
                new ValuedStack(Identifier.fromNamespaceAndPath("minecraft", "oak_log"), 1, 40)));
        queue.charge(25);

        PanelRow row = CruciblePanelRows.meltRow(queue.head(), queue.waiting().size(),
                item -> item.equals(diamond) ? new CruciblePanelRows.ItemIcon(atlas, diamondUv) : null);

        assertEquals(atlas, row.icon());
        assertEquals(diamondUv, row.iconUv());
        assertEquals("25% +1", textOf(row));
        assertEquals(PanelPainter.TEXT_COLOR, row.segments().getFirst().color());
        assertEquals(0xFF888888, row.segments().getLast().color());
    }

    /** An empty queue paints no melting row. */
    @Test
    void emptyQueuePaintsNoMeltRow() {
        assertNull(CruciblePanelRows.meltRow(null, 0, item -> null));
    }

    /** A type full in both the reservoir and the pool reads their long sum. */
    @Test
    void typeFullInBothStoresReadsLongTotal() {
        GooContents reservoir = new GooContents(Map.of(GooTypes.ROCK, 2_000_000_000));
        GooContents pool = new GooContents(Map.of(GooTypes.ROCK, 2_000_000_000));
        assertEquals(4_000_000_000L, CruciblePanelRows.totalOf(reservoir, pool, GooTypes.ROCK));
    }

    /** 12 bought blaze ticks and 50 mB blaze paint one heat row reading 212 ticks as "10.6s". */
    @Test
    void blazeHeatRowReadsSeconds() {
        CrucibleHeat heat = new CrucibleHeat();
        heat.set(12, BLAZE);
        List<PanelRow> rows = rowsFor(heat, new GooContents(Map.of(GooTypes.BLAZE, 50)));

        assertEquals(2, rows.size());
        assertEquals(PanelPainter.gooIcon(GooTypes.BLAZE), rows.getLast().icon());
        assertEquals("10.6s", textOf(rows.getLast()));
    }

    /** 100 mB unstable and no blaze paint one heat row with unstable's icon reading "5.0s". */
    @Test
    void unstableHeatRowReadsSeconds() {
        List<PanelRow> rows = rowsFor(new CrucibleHeat(), new GooContents(Map.of(GooTypes.UNSTABLE, 100)));

        assertEquals(2, rows.size());
        assertEquals(PanelPainter.gooIcon(GooTypes.UNSTABLE), rows.getLast().icon());
        assertEquals("5.0s", textOf(rows.getLast()));
    }

    /** A crucible holding neither heat nor fuel goo paints no heat row. */
    @Test
    void noHeatNoFuelPaintsNoHeatRow() {
        assertTrue(CrucibleFuelDisplay.heatRows(new CrucibleHeat().forecast(BURN_ORDER, DRAIN, type -> 0)).isEmpty());
    }

    /** 100 mB blaze and 200 mB unstable paint the combo row "2.5s" above unstable's "5.0s". */
    @Test
    void comboRowSitsAboveTheUnstableRemainder() {
        List<PanelRow> heatRows = heatRowsFor(new CrucibleHeat(),
                new GooContents(Map.of(GooTypes.BLAZE, 100, GooTypes.UNSTABLE, 200)));

        assertEquals(2, heatRows.size());
        assertCombo(heatRows.getFirst(), "2.5s");
        assertLone(heatRows.getLast(), GooTypes.UNSTABLE, "5.0s");
    }

    /** 400 mB blaze and 40 mB unstable paint the combo row "1.0s" above blaze's "72.0s". */
    @Test
    void comboRowSitsAboveTheBlazeRemainder() {
        List<PanelRow> heatRows = heatRowsFor(new CrucibleHeat(),
                new GooContents(Map.of(GooTypes.BLAZE, 400, GooTypes.UNSTABLE, 40)));

        assertEquals(2, heatRows.size());
        assertCombo(heatRows.getFirst(), "1.0s");
        assertLone(heatRows.getLast(), GooTypes.BLAZE, "72.0s");
    }

    /** The combo row measures two icons with their gaps before its text. */
    @Test
    void comboRowMeasuresBothIcons() {
        PanelRow combo = heatRowsFor(new CrucibleHeat(),
                new GooContents(Map.of(GooTypes.BLAZE, 2, GooTypes.UNSTABLE, 2))).getFirst();
        float oneIcon = PanelPainter.ICON_SIZE + PanelPainter.ICON_TEXT_GAP;

        assertEquals(oneIcon + oneIcon + 4 * 6, combo.width(text -> text.length() * 6));
    }

    /** 10 melt ticks on 400 mB blaze and 40 mB unstable halve the combo row while blaze's row holds still. */
    @Test
    void remainderHoldsStillWhileTheComboBurns() {
        CrucibleHeat heat = new CrucibleHeat();
        MapStock stock = new MapStock(Map.of(GooTypes.BLAZE, 400, GooTypes.UNSTABLE, 40));
        for (int tick = 0; tick < 10; tick++) {
            heat.burnMeltTick(true, BURN_ORDER, DRAIN, stock);
        }
        List<PanelRow> heatRows = heatRowsFor(heat, stock.contents());

        assertEquals(2, heatRows.size());
        assertCombo(heatRows.getFirst(), "0.5s");
        assertLone(heatRows.getLast(), GooTypes.BLAZE, "72.0s");
    }
}
