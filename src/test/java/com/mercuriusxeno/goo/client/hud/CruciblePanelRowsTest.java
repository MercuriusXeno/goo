package com.mercuriusxeno.goo.client.hud;

import com.mercuriusxeno.goo.GooConfig;
import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.GooTypes;
import com.mercuriusxeno.goo.block.crucible.CrucibleHeat;
import com.mercuriusxeno.goo.block.crucible.FuelGrade;
import com.mercuriusxeno.goo.item.GooContents;
import net.minecraft.resources.ResourceKey;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the crucible HUD's rows: the per-type total of reservoir and pool, which reads
 * past the int range rather than wrapping (decision diagnose-then-fix-crucible-overflow),
 * and the heat rows reading seconds (decision heat-row-reads-seconds). Grades are built
 * from GooConfig's defaults, since the unit suite loads no config.
 */
class CruciblePanelRowsTest {

    private static final FuelGrade BLAZE = new FuelGrade(
            GooTypes.BLAZE, GooConfig.DEFAULT_BLAZE_TICKS_PER_MB, GooConfig.DEFAULT_BLAZE_MELT_RATE);
    private static final FuelGrade UNSTABLE = new FuelGrade(
            GooTypes.UNSTABLE, GooConfig.DEFAULT_UNSTABLE_TICKS_PER_MB, GooConfig.DEFAULT_UNSTABLE_MELT_RATE);
    private static final List<FuelGrade> BURN_ORDER = List.of(UNSTABLE, BLAZE);

    /**
     * Builds the rows the HUD paints for a crucible holding the given heat and reservoir.
     *
     * @param heat      the crucible's heat
     * @param reservoir the reservoir goo contents
     * @return the rows top to bottom
     */
    private static List<PanelRow> rowsFor(CrucibleHeat heat, GooContents reservoir) {
        List<CrucibleHeat.FuelBurn> burns = heat.forecast(BURN_ORDER, type -> volumeOf(reservoir, type));
        return CruciblePanelRows.rows(reservoir, GooContents.EMPTY, CrucibleFuelDisplay.heatRows(burns));
    }

    private static int volumeOf(GooContents contents, ResourceKey<GooTypeDefinition> type) {
        return contents.getAll().getOrDefault(type, 0);
    }

    private static String textOf(PanelRow row) {
        return row.segments().stream().map(PanelRow.TextSegment::text).collect(Collectors.joining());
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
        assertTrue(CrucibleFuelDisplay.heatRows(new CrucibleHeat().forecast(BURN_ORDER, type -> 0)).isEmpty());
    }
}
