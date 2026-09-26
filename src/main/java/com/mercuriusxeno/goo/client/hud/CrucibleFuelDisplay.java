package com.mercuriusxeno.goo.client.hud;

import com.mercuriusxeno.goo.block.crucible.CrucibleHeat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Supplies the crucible HUD panel's heat rows: per fuel grade, its goo icon and
 * the seconds its heat and stock last (decision heat-row-reads-seconds).
 */
final class CrucibleFuelDisplay {
    /** Heat text color (orange, the blaze color). */
    private static final int HEAT_COLOR = 0xFFFF6600;
    /** Game ticks in one second. */
    private static final double TICKS_PER_SECOND = 20.0;
    /** Seconds to one decimal place, suffixed "s". */
    private static final String SECONDS_FORMAT = "%.1fs";

    private CrucibleFuelDisplay() {}

    /**
     * Builds one heat row per burn: the fuel's goo icon, then the seconds it lasts.
     *
     * @param burns the melt ticks each fuel grade holds, in burn order
     * @return the rows, empty when the crucible holds neither heat nor fuel goo
     */
    static List<PanelRow> heatRows(List<CrucibleHeat.FuelBurn> burns) {
        List<PanelRow> rows = new ArrayList<>();
        for (CrucibleHeat.FuelBurn burn : burns) {
            rows.add(PanelRow.iconText(PanelPainter.gooIcon(burn.grade().fuel()), seconds(burn.ticks()), HEAT_COLOR));
        }
        return rows;
    }

    /**
     * Formats melt ticks as seconds to one decimal place.
     *
     * @param ticks the melt ticks
     * @return the seconds text, such as "10.6s"
     */
    static String seconds(long ticks) {
        return String.format(Locale.ROOT, SECONDS_FORMAT, ticks / TICKS_PER_SECOND);
    }
}
